#Requires -Version 5.1
<#
.SYNOPSIS
  Instala y arranca el servidor Minecraft completo con un solo comando.

.EXAMPLE
  .\install.ps1
  .\install.ps1 -SkipDataDownload   # si ya tienes server-data/
  .\install.ps1 -ImportArchive "D:\backup\server-data.7z.001"
#>
param(
  [switch] $SkipDataDownload,
  [string] $ImportArchive = "",
  [string] $ReleaseRepo = "Ferxas/minecraft-docker-server",
  [string] $ReleaseTag = "server-data-v1"
)

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$ServerData = Join-Path $Root "server-data"
$MysqlData = Join-Path $Root "mysql-data"
$DownloadDir = Join-Path $Root "bootstrap" "download"

function Test-ServerDataReady {
  return (Test-Path (Join-Path $ServerData "eula.txt")) -and
         (Test-Path (Join-Path $ServerData "plugins")) -and
         ((Get-ChildItem (Join-Path $ServerData "plugins") -Filter "*.jar" -ErrorAction SilentlyContinue | Measure-Object).Count -gt 0)
}

function Ensure-Docker {
  if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker no está instalado. Instala Docker Desktop: https://www.docker.com/products/docker-desktop/"
  }
  docker info *> $null
  if ($LASTEXITCODE -ne 0) { throw "Docker no está corriendo. Abre Docker Desktop e inténtalo de nuevo." }
}

function Import-ArchiveParts {
  param([string]$FirstPart)
  $7z = Get-Command 7z -ErrorAction SilentlyContinue
  if (-not $7z) { $7z = Get-Command "C:\Users\ferxas\scoop\shims\7z.exe" -ErrorAction SilentlyContinue }
  if (-not $7z) { throw "7-Zip no encontrado. Instala 7z o pasa -SkipDataDownload si server-data/ ya existe." }
  Write-Host "Extrayendo $FirstPart ..." -ForegroundColor Cyan
  & $7z x $FirstPart -o"$Root" -y | Out-Host
}

function Download-ReleaseData {
  if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw @"
Faltan los datos del servidor (server-data/ + mysql-data/).

Opciones:
  1) Instala GitHub CLI y ejecuta de nuevo:  winget install GitHub.cli
  2) Descarga manualmente el release '$ReleaseTag' de $ReleaseRepo y ejecuta:
     .\install.ps1 -ImportArchive ruta\server-data.7z.001
  3) Copia las carpetas server-data/ y mysql-data/ junto al repo.
"@
  }
  New-Item -ItemType Directory -Force -Path $DownloadDir | Out-Null
  Write-Host "Descargando datos del servidor desde GitHub Release $ReleaseTag ..." -ForegroundColor Cyan
  gh release download $ReleaseTag --repo $ReleaseRepo --dir $DownloadDir
  $first = Get-ChildItem $DownloadDir -Filter "server-data*.7z.001" | Select-Object -First 1
  if (-not $first) { throw "No se encontró server-data*.7z.001 en el release." }
  Import-ArchiveParts -FirstPart $first.FullName
}

Write-Host "=== Con Los Pibes — Minecraft Server ===" -ForegroundColor Green
Ensure-Docker

if ($ImportArchive) {
  Import-ArchiveParts -FirstPart (Resolve-Path $ImportArchive).Path
}
elseif (-not $SkipDataDownload -and -not (Test-ServerDataReady)) {
  Download-ReleaseData
}

if (-not (Test-ServerDataReady)) {
  throw "server-data/ incompleto. Usa -ImportArchive o publica/descarga el release de datos."
}

New-Item -ItemType Directory -Force -Path $MysqlData | Out-Null

Push-Location $Root
try {
  Write-Host "Iniciando stack (mc-init + mysql + paper)..." -ForegroundColor Cyan
  docker compose up -d
  Write-Host @"

Listo.
  Java:    localhost:25565
  Bedrock: localhost:19132 (Geyser)
  Logs:    docker logs mc -f

"@ -ForegroundColor Green
}
finally {
  Pop-Location
}
