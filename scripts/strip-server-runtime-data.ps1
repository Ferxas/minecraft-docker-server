#Requires -Version 5.1
<#
.SYNOPSIS
  Borra datos de runtime (MySQL, jugadores, permisos) conservando plugins, configs y mapas.
#>
param(
  [string] $Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"
$ServerData = Join-Path $Root "server-data"
$MysqlData = Join-Path $Root "mysql-data"

function Write-FreshLog([string]$Msg) {
  Write-Host "[fresh] $Msg" -ForegroundColor Yellow
}

if (-not (Test-Path $ServerData)) {
  throw "No existe server-data/ en $Root"
}

Write-FreshLog "Modo fresh: conservando plugins, configs y mundos; borrando datos de jugadores/DB..."

if (Test-Path $MysqlData) {
  Write-FreshLog "  mysql-data/"
  Get-ChildItem $MysqlData -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path $MysqlData | Out-Null

$pluginPatterns = @(
  "LuckPerms\luckperms-h2*.db*",
  "LuckPerms\yaml-storage\*",
  "Essentials\userdata\*",
  "Essentials\usermap.csv",
  "Essentials\uuids.bin",
  "DiscordSRV\accounts.aof",
  "DiscordSRV\linked*",
  "CoreProtect\database.db",
  "Plan\*.db",
  "Plan\*.db-journal",
  "Skript\variables.csv",
  "AdvancedReplay\replays\*",
  "PlayerPoints\storage.db"
)

$pluginsDir = Join-Path $ServerData "plugins"
if (Test-Path $pluginsDir) {
  foreach ($pat in $pluginPatterns) {
    Get-ChildItem (Join-Path $pluginsDir $pat) -Force -ErrorAction SilentlyContinue |
      ForEach-Object {
        Write-FreshLog "  plugins\$($_.FullName.Substring($pluginsDir.Length + 1))"
        Remove-Item $_.FullName -Recurse -Force
      }
  }
}

foreach ($dir in @(
    "plugins\LuckPerms\yaml-storage",
    "plugins\Essentials\userdata",
    "plugins\AdvancedReplay\replays"
  )) {
  $p = Join-Path $ServerData $dir
  if (Test-Path $p) {
    Get-ChildItem $p -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
  }
}

foreach ($f in @("usercache.json", "banned-players.json", "banned-ips.json", "ops.json", "whitelist.json")) {
  $p = Join-Path $ServerData $f
  if (Test-Path $p) {
    Write-FreshLog "  $f"
    Remove-Item $p -Force
  }
}

$skipWorlds = @(
  "plugins", "config", "logs", "cache", "versions", "libraries",
  "backups", ".cache", "bluemap", "emotes", "iDisguise-Dummy"
)

Get-ChildItem $ServerData -Directory -ErrorAction SilentlyContinue | ForEach-Object {
  if ($skipWorlds -contains $_.Name) { return }
  $levelDat = Join-Path $_.FullName "level.dat"
  if (-not (Test-Path $levelDat)) { return }
  Write-FreshLog "  mundo $($_.Name) → playerdata/stats/advancements"
  foreach ($sub in @("playerdata", "stats", "advancements", "data\playerdata", "data\stats", "data\advancements")) {
    $p = Join-Path $_.FullName $sub
    if (Test-Path $p) { Remove-Item $p -Recurse -Force }
  }
  foreach ($f in @("uid.dat", "session.lock")) {
    $p = Join-Path $_.FullName $f
    if (Test-Path $p) { Remove-Item $p -Force }
  }
}

$logsDir = Join-Path $ServerData "logs"
if (Test-Path $logsDir) {
  Write-FreshLog "  logs/"
  Get-ChildItem $logsDir -Include *.log, *.log.gz -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
}

Write-FreshLog "Listo. Configura permisos de nuevo con LuckPerms al primer arranque."
