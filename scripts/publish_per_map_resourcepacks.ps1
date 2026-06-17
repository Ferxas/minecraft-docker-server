#Requires -Version 5.1
<#
.SYNOPSIS
  Crea un release en GitHub con los dos ZIP por mapa (debe existir merged-packs/server-bunker-map.zip y server-krothole-map.zip).
  El tag por defecto coincide con PackManagerPro (per-map-packs-v1).

.EXAMPLE
  .\scripts\build_map_merged_packs.ps1
  .\scripts\publish_per_map_resourcepacks.ps1
#>
param(
  [string] $Tag = "per-map-packs-v1",
  [string] $Repo = "Ferxas/mc-compose-resourcepacks"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$b = Join-Path $Root "merged-packs\server-bunker-map.zip"
$k = Join-Path $Root "merged-packs\server-krothole-map.zip"
if (-not (Test-Path $b)) { Write-Error "Ejecuta primero scripts\build_map_merged_packs.ps1 (falta $b)" }
if (-not (Test-Path $k)) { Write-Error "Ejecuta primero scripts\build_map_merged_packs.ps1 (falta $k)" }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
  Write-Error "Instala GitHub CLI (gh) y gh auth login. Luego: gh release create $Tag `"$b`" `"$k`" --repo $Repo --title `"Packs Bunker + KROTHOLE (por mundo)`""
}

gh release create $Tag $b $k --repo $Repo --title "Packs Bunker + KROTHOLE (por mundo)" --notes "Generados con mc-compose scripts/build_map_merged_packs.ps1 (base servidor + worlds/.../resources)."
Write-Host "Listo. PackManagerPro ya usa:" -ForegroundColor Green
Write-Host "  https://github.com/$Repo/releases/download/$Tag/server-bunker-map.zip"
Write-Host "  https://github.com/$Repo/releases/download/$Tag/server-krothole-map.zip"
Write-Host "Copia plugin-configs/PackManagerPro/config.yml al servidor y: pmp reload" -ForegroundColor Yellow
