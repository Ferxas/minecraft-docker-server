#Requires -Version 5.1
<#
.SYNOPSIS
  Instala y arranca el servidor Minecraft completo con un solo comando.

.EXAMPLE
  .\install.ps1
  .\install.ps1 -Fresh                  # plugins + mapas, sin datos de jugadores/MySQL
  .\install.ps1 -SkipDataDownload   # si ya tienes server-data/
  .\install.ps1 -ImportArchive "D:\backup\server-data.7z.001"
#>
param(
  [switch] $Fresh,
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

function Wait-MinecraftReady {
  param([int]$TimeoutMinutes = 25)
  Write-Host "Esperando a que Paper responda (muchas plugins: suele tardar 10-15 min)..." -ForegroundColor Yellow
  $deadline = (Get-Date).AddMinutes($TimeoutMinutes)
  while ((Get-Date) -lt $deadline) {
    $status = docker inspect mc --format "{{.State.Health.Status}}" 2>$null
    if ($status -eq "healthy") {
      Write-Host "Servidor listo para conectar." -ForegroundColor Green
      return $true
    }
    if ($status -eq "unhealthy") {
      $tail = docker logs mc --tail 1 2>$null
      Write-Host "  ... cargando ($tail)"
    }
    Start-Sleep -Seconds 15
  }
  Write-Host "Paper aún no responde. Sigue con: docker logs mc -f" -ForegroundColor Yellow
  return $false
}

function Import-ArchiveParts {
  param([string]$FirstPart)
  $7z = Find-7Zip
  if (-not $7z) { throw "7-Zip no encontrado. Instala 7-Zip (https://www.7-zip.org/) o pasa -SkipDataDownload si server-data/ ya existe." }
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

if ($Fresh) {
  & (Join-Path $Root "scripts/strip-server-runtime-data.ps1") -Root $Root
}

New-Item -ItemType Directory -Force -Path $MysqlData | Out-Null

Push-Location $Root
try {
  Write-Host "Iniciando stack (mc-init + mysql + paper)..." -ForegroundColor Cyan
  docker compose up -d
  $ready = Wait-MinecraftReady
  $readyLine = if ($ready) { "Listo." } else { "Stack arriba; Paper sigue cargando — esperá healthy antes de entrar." }
  Write-Host @"

$readyLine
  Java:    localhost:25565  — launcher Java Edition 1.21.11
  Bedrock: localhost:19132/UDP (Geyser) — no uses el puerto Java en Bedrock
  playit:  túnel TCP -> 127.0.0.1:25565 (Java); UDP -> 127.0.0.1:19132 (Bedrock)
  Logs:    docker logs mc -f

"@ -ForegroundColor Green
  if ($Fresh) {
    Write-Host @"
Modo fresh: MySQL, LuckPerms y datos de jugadores están vacíos.
  Reconfigura permisos (/lp editor) y DiscordSRV si lo usas.

"@ -ForegroundColor Yellow
  }
}
finally {
  Pop-Location
}
