#Requires -Version 5.1
<#
.SYNOPSIS
  Empaqueta server-data + mysql-data para GitHub Release (partes 7z < 2 GB).
.EXAMPLE
  .\scripts\package-server-data.ps1
  .\scripts\package-server-data.ps1 -FromVolume
#>
param(
  [switch] $FromVolume,
  [string] $McVolume = "f130bdbf22db4f205c589128612b40005b01183c8f1632412307164453b95e69",
  [string] $MysqlVolume = "mc-compose_mc-mysql-data"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$OutDir = Join-Path $Root "bootstrap" "release"
$ServerData = Join-Path $Root "server-data"
$MysqlData = Join-Path $Root "mysql-data"
$ArchiveBase = Join-Path $OutDir "server-data-v1"

if ($FromVolume) {
  & (Join-Path $PSScriptRoot "export-volume-to-server-data.ps1") -Force
}

if (-not (Test-Path (Join-Path $ServerData "eula.txt"))) {
  throw "server-data/ incompleto. Ejecuta export-volume-to-server-data.ps1 o -FromVolume."
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Find-7Zip {
  $cmd = Get-Command 7z -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $candidates = @(
    (Join-Path $env:USERPROFILE "scoop\shims\7z.exe"),
    (Join-Path ${env:ProgramFiles} "7-Zip\7z.exe"),
    (Join-Path ${env:ProgramFiles(x86)} "7-Zip\7z.exe")
  )
  foreach ($path in $candidates) {
    if ($path -and (Test-Path -LiteralPath $path)) { return $path }
  }
  return $null
}
$7z = Find-7Zip
if (-not $7z) { throw "7-Zip required" }

$sources = @($ServerData)
if (Test-Path $MysqlData) { $sources += $MysqlData }

Write-Host "Creating 7z volumes (direct, no staging copy)..." -ForegroundColor Cyan
Remove-Item "$ArchiveBase.7z.*" -Force -ErrorAction SilentlyContinue
Push-Location $Root
try {
  & $7z a -mx1 -v1800m "$ArchiveBase.7z" @sources
  if ($LASTEXITCODE -gt 1) { throw "7z failed with exit code $LASTEXITCODE" }
}
finally {
  Pop-Location
}

Write-Host "Release files:" -ForegroundColor Green
Get-ChildItem $OutDir -Filter "server-data-v1.7z.*" | Format-Table Name, @{N='MB';E={[math]::Round($_.Length/1MB,1)}}

Write-Host @"

Subir a GitHub:
  gh release create server-data-v1 bootstrap/release/server-data-v1.7z.* --repo Ferxas/minecraft-docker-server --title "Server data v1"

"@ -ForegroundColor Yellow
