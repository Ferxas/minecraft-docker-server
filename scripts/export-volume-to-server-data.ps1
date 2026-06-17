#Requires -Version 5.1
param(
  [switch] $Force,
  [string] $McVolume = "f130bdbf22db4f205c589128612b40005b01183c8f1632412307164453b95e69",
  [string] $MysqlVolume = "mc-compose_mc-mysql-data"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ServerData = Join-Path $Root "server-data"
$MysqlData = Join-Path $Root "mysql-data"

function Test-DirHasContent([string]$Path) {
  return (Test-Path $Path) -and ((Get-ChildItem -Force $Path -ErrorAction SilentlyContinue | Measure-Object).Count -gt 0)
}

function Copy-VolumeToDir {
  param([string]$VolumeName, [string]$DestDir, [string]$Label)

  if ((Test-DirHasContent $DestDir) -and -not $Force) {
    Write-Host "[skip] $Label already at $DestDir" -ForegroundColor Yellow
    return
  }

  if ($Force -and (Test-Path $DestDir)) {
    Write-Host "Removing incomplete $DestDir ..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force $DestDir
  }

  Write-Host "=== Export $Label from volume '$VolumeName' ===" -ForegroundColor Cyan
  New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
  docker run --rm -v "${VolumeName}:/src:ro" -v "${DestDir}:/dest" alpine sh -c "cp -a /src/. /dest/ && chown -R 1000:1000 /dest"
  Write-Host "Done: $Label" -ForegroundColor Green
}

Copy-VolumeToDir -VolumeName $McVolume -DestDir $ServerData -Label "Minecraft /data"
Copy-VolumeToDir -VolumeName $MysqlVolume -DestDir $MysqlData -Label "MySQL"
