#Requires -Version 5.1
<#
.SYNOPSIS
  Genera dos ZIP fusionados (pack servidor mc-packs + resources/ de cada mapa).
  Salida: merged-packs/server-bunker-map.zip (~235 MiB) y merged-packs/server-krothole-map.zip (~50 MiB).
  Siguiente paso: publicar en GitHub con publish_per_map_resourcepacks.ps1 (las URLs en PackManagerPro apuntan a ese release).
#>
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$BaseUrl = "https://download.mc-packs.net/pack/f2b7ea3a45465ee72e93494e383604047543deed.zip"
$OutDir = Join-Path $Root "merged-packs"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$py = Join-Path $Root "scripts\merge_resourcepacks.py"
$BunkerRes = Join-Path $Root "worlds\Bunker of the infected\resources"
$KroRes = Join-Path $Root "worlds\KROTHOLE Nouvelle Ere\resources"
if (-not (Test-Path $BunkerRes)) { Write-Error "No existe: $BunkerRes" }
if (-not (Test-Path $KroRes)) { Write-Error "No existe: $KroRes" }

Set-Location $Root
Write-Host "Fusionando Bunker (tarda, descarga base)…" -ForegroundColor Cyan
python $py --base $BaseUrl --overlay $BunkerRes -o (Join-Path $OutDir "server-bunker-map.zip")
Write-Host "Fusionando KROTHOLE…" -ForegroundColor Cyan
python $py --base $BaseUrl --overlay $KroRes -o (Join-Path $OutDir "server-krothole-map.zip")
Get-ChildItem $OutDir -Filter "server-*-map.zip" | ForEach-Object { Write-Host ("  {0}  {1:N1} MiB" -f $_.Name, ($_.Length / 1MB)) }
