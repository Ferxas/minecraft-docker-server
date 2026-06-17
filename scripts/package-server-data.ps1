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
$Staging = Join-Path $env:TEMP "conlospibes-pack-staging"
if (Test-Path $Staging) { Remove-Item -Recurse -Force $Staging }
New-Item -ItemType Directory -Force -Path "$Staging/server-data", "$Staging/mysql-data" | Out-Null

Write-Host "Staging copy..." -ForegroundColor Cyan
robocopy $ServerData "$Staging/server-data" /MIR /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
if (Test-Path $MysqlData) {
  robocopy $MysqlData "$Staging/mysql-data" /MIR /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
}

$7z = "C:\Users\ferxas\scoop\shims\7z.exe"
if (-not (Test-Path $7z)) { $7z = (Get-Command 7z -ErrorAction SilentlyContinue).Source }
if (-not $7z) { throw "7-Zip required" }

Write-Host "Creating 7z volumes..." -ForegroundColor Cyan
Remove-Item "$ArchiveBase.7z.*" -Force -ErrorAction SilentlyContinue
& $7z a -mx1 -v1800m "$ArchiveBase.7z" "$Staging/*"

Write-Host "Release files:" -ForegroundColor Green
Get-ChildItem $OutDir -Filter "server-data-v1.7z.*" | Format-Table Name, @{N='MB';E={[math]::Round($_.Length/1MB,1)}}

Write-Host @"

Subir a GitHub:
  gh release create server-data-v1 bootstrap/release/server-data-v1.7z.* --repo Ferxas/conlospibes-server --title "Server data v1"

"@ -ForegroundColor Yellow
