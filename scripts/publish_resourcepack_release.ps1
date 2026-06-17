#Requires -Version 5.1
<#
.SYNOPSIS
  Crea un release en GitHub (gh CLI) y adjunta un ZIP de resource pack.
  Requiere: gh autenticado (gh auth login). Repo por defecto: Ferxas/mc-compose-resourcepacks

.EXAMPLE
  .\publish_resourcepack_release.ps1 -ZipPath "..\merged-packs\server-bunker-krothole-unified-mcpack.zip" -Tag "bunker-krothole-unified-v2"
#>
param(
  [Parameter(Mandatory = $true)]
  [string] $ZipPath,
  [string] $Tag = ("pack-" + (Get-Date -Format "yyyyMMdd-HHmmss")),
  [string] $Repo = "Ferxas/mc-compose-resourcepacks",
  [string] $Title = "Resource pack release"
)

$ErrorActionPreference = "Stop"
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
  Write-Error "Instala GitHub CLI: https://cli.github.com/ y ejecuta gh auth login"
}
$z = Resolve-Path $ZipPath
gh release create $Tag $z.Path --repo $Repo --title $Title --notes "Pack ZIP adjunto. SHA1 (local): $((Get-FileHash -Algorithm SHA1 $z.Path).Hash.ToLower())"
Write-Host "Listo. URL típica del asset:" -ForegroundColor Green
Write-Host "https://github.com/$Repo/releases/download/$Tag/$([System.IO.Path]::GetFileName($z.Path))"
