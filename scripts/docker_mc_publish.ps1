#Requires -Version 5.1
<#
.SYNOPSIS
  Copia plugins/configs/mundos del repo a ./server-data (idempotente: no sobrescribe).
  Sustituye el flujo antiguo contra volumen Docker externo.

.PARAMETER ForceConfigs
  Sobrescribe configs de plugins listados (PackManagerPro, Via*).
#>
param(
  [switch] $SkipWorlds,
  [switch] $ForceConfigs
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ServerData = Join-Path $Root "server-data"

if (-not (Test-Path $ServerData)) {
  New-Item -ItemType Directory -Force -Path $ServerData | Out-Null
}

Write-Host "=== mc-init (publish repo -> server-data) ===" -ForegroundColor Cyan
docker compose -f (Join-Path $Root "docker-compose.yml") run --rm --no-deps mc-init

if ($ForceConfigs) {
  Write-Host "=== Force-copy selected configs ===" -ForegroundColor Cyan
  $pairs = @(
    @("plugin-configs/PackManagerPro/config.yml", "plugins/PackManagerPro/config.yml"),
    @("plugin-configs/ViaVersion/config.yml", "plugins/ViaVersion/config.yml"),
    @("plugin-configs/ViaBackwards/config.yml", "plugins/ViaBackwards/config.yml"),
    @("plugin-configs/ViaRewind/config.yml", "plugins/ViaRewind/config.yml")
  )
  foreach ($p in $pairs) {
    $src = Join-Path $Root $p[0]
    $dest = Join-Path $ServerData $p[1]
    if (Test-Path $src) {
      New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
      Copy-Item -Force $src $dest
      Write-Host "  forced $($p[1])"
    }
  }
}

Write-Host "Listo. Arranca con: docker compose up -d" -ForegroundColor Green
