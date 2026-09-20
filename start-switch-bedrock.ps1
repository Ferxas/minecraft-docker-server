#Requires -Version 5.1
<#
.SYNOPSIS
  Arranca Geyser Standalone en Windows (UDP 19132) hacia el Paper en WSL/Docker.
  Dejá esta ventana abierta mientras jugás desde la Switch.
#>
param(
  [string] $JavaHost = "127.0.0.1",
  [int] $JavaPort = 25565,
  [int] $BedrockPort = 19132
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Dir = Join-Path $Root "tools\switch-bedrock"
$Jar = Join-Path $Dir "Geyser-Standalone.jar"
$Key = Join-Path $Dir "key.pem"
$Cfg = Join-Path $Dir "config.yml"

if (-not (Test-Path $Jar)) { throw "Missing $Jar" }
if (-not (Test-Path $Key)) { throw "Missing floodgate key: $Key" }
if (-not (Test-Path $Cfg)) { throw "Missing $Cfg — run Geyser once to generate it." }

# Ensure auth-type floodgate
$raw = Get-Content $Cfg -Raw
if ($raw -notmatch '(?m)^\s*auth-type:\s*floodgate\s*$') {
  $raw = $raw -replace '(?m)^(\s*auth-type:\s*)\S+\s*$', '${1}floodgate'
  Set-Content $Cfg -Value $raw -Encoding UTF8 -NoNewline
  Write-Host "Patched auth-type -> floodgate"
}

# Firewall UDP 19132 (ignore if no admin)
try {
  $rule = Get-NetFirewallRule -DisplayName "MC Geyser Bedrock 19132" -EA SilentlyContinue
  if (-not $rule) {
    New-NetFirewallRule -DisplayName "MC Geyser Bedrock 19132" -Direction Inbound -Protocol UDP -LocalPort $BedrockPort -Action Allow -Profile Any -EA Stop | Out-Null
    Write-Host "Firewall rule added for UDP $BedrockPort"
  }
} catch {
  Write-Warning "No pude crear regla de firewall (corrê como Admin si la Switch no conecta): $($_.Exception.Message)"
}

# Java backend reachable?
$tcp = Test-NetConnection $JavaHost -Port $JavaPort -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
  throw "Paper no responde en ${JavaHost}:${JavaPort}. Levantá el stack: .\start-wsl-live.ps1 up"
}

# Kill old geyser on this jar
Get-CimInstance Win32_Process -EA SilentlyContinue |
  Where-Object { $_.CommandLine -and $_.CommandLine -like "*Geyser-Standalone.jar*" } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -EA SilentlyContinue }

$wifi = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -eq "Wi-Fi" -and $_.IPAddress -like "192.168.*" } | Select-Object -First 1).IPAddress
Write-Host ""
Write-Host "Geyser Standalone"
Write-Host "  Bedrock : 0.0.0.0:${BedrockPort} (LAN $($wifi):${BedrockPort})"
Write-Host "  Java    : ${JavaHost}:${JavaPort}"
Write-Host "  Auth    : floodgate"
Write-Host "Dejá esta ventana abierta. Ctrl+C para parar."
Write-Host ""

Set-Location $Dir
# --enable-native-access silences JNA warning on Java 25+
& java --enable-native-access=ALL-UNNAMED -Xms256M -Xmx1G -jar $Jar --nogui
