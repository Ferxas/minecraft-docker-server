#Requires -Version 5.1
<#
.SYNOPSIS
  Abre puertos en el firewall de Windows para Java + Bedrock (Geyser).

.EXAMPLE
  .\scripts\setup-network.ps1
  .\scripts\setup-network.ps1 -Remove
#>
param([switch] $Remove)

$ErrorActionPreference = "Stop"

$rules = @(
  @{
    Name     = "ConLosPibes Minecraft Java"
    Port     = 25565
    Protocol = "TCP"
  },
  @{
    Name     = "ConLosPibes Geyser Bedrock"
    Port     = 19132
    Protocol = "UDP"
  }
)

function Ensure-Rule {
  param($Rule)
  $existing = Get-NetFirewallRule -DisplayName $Rule.Name -ErrorAction SilentlyContinue
  if ($Remove) {
    if ($existing) {
      Remove-NetFirewallRule -DisplayName $Rule.Name
      Write-Host "Removed: $($Rule.Name)" -ForegroundColor Yellow
    }
    return
  }
  if ($existing) {
    Write-Host "Already exists: $($Rule.Name)" -ForegroundColor DarkGray
    return
  }
  New-NetFirewallRule `
    -DisplayName $Rule.Name `
    -Direction Inbound `
    -Action Allow `
    -Protocol $Rule.Protocol `
    -LocalPort $Rule.Port `
    -Profile Any | Out-Null
  Write-Host "Added: $($Rule.Name) ($($Rule.Protocol) $($Rule.Port))" -ForegroundColor Green
}

Write-Host "=== Con Los Pibes - firewall (Windows) ===" -ForegroundColor Cyan
foreach ($r in $rules) { Ensure-Rule -Rule $r }

if (-not $Remove) {
  Write-Host ""
  Write-Host "Firewall local listo. Falta reenvio en el router (NAT):" -ForegroundColor Yellow
  Write-Host "  25565/TCP  -> IP local de esta PC (Java)"
  Write-Host "  19132/UDP  -> IP local de esta PC (Bedrock/Geyser)"
  Write-Host ""
  $ip = $null
  try {
    $ip = Invoke-RestMethod -Uri "https://api.ipify.org" -TimeoutSec 5
    Write-Host ("IP publica (Java): " + $ip + ":25565") -ForegroundColor Green
  } catch {
    Write-Host "No se pudo obtener IP publica (whatismyip.com)." -ForegroundColor DarkGray
    $ip = "TU_IP_PUBLICA"
  }
  if (-not $ip) { $ip = "TU_IP_PUBLICA" }
  Write-Host ""
  Write-Host "Xbox/Bedrock: DNS secundario 104.238.130.180 (BedrockConnect)" -ForegroundColor Cyan
  Write-Host ("  Agregar servidor Bedrock: " + $ip + ":19132")
}
