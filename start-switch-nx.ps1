#Requires -Version 5.1
<#
.SYNOPSIS
  Arranca el Geyser Standalone solo para la Switch hacia el Paper en WSL/Docker.
  Usa Geyser 2.12.0 porque la Switch va en Bedrock 26.30 y Geyser 2.12.1 ya no la acepta.
  La Switch entra por UDP 19132 a lan_relay.py, que reenvía a Geyser (127.0.0.1:19136) y anuncia 26.30
  en el ping LAN para que la partida aparezca en la pestaña Mundos.
  ctest_server.py (TCP 80) responde al test de conexión de la Switch (ctest.cdn.nintendo.net redirigido a este PC
  en atmosphere/hosts/default.txt) para que Minecraft no se crea offline y active el juego en red.
  Sin cuentas: auth-type offline y validate-bedrock-login false. Solo LAN, sin port-forward en el router.
  Dejá esta ventana abierta mientras jugás desde la Switch.
#>
param(
  [string] $JavaHost = "127.0.0.1",
  [int] $JavaPort = 25565,
  [int] $BedrockPort = 19132,
  [int] $GeyserPort = 19136
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Dir = Join-Path $Root "tools\switch-bedrock-nx"
$Jar = Join-Path $Dir "Geyser-Switch-NX.jar"
$Cfg = Join-Path $Dir "config.yml"
$Relay = Join-Path $Dir "lan_relay.py"
$Ctest = Join-Path $Dir "ctest_server.py"

if (-not (Test-Path $Jar)) { throw "Missing $Jar" }
if (-not (Test-Path $Cfg)) { throw "Missing $Cfg" }
if (-not (Test-Path $Relay)) { throw "Missing $Relay" }
if (-not (Test-Path $Ctest)) { throw "Missing $Ctest" }

# Firewall UDP 19132 (ignore if no admin)
try {
  $rule = Get-NetFirewallRule -DisplayName "MC Geyser Bedrock $BedrockPort" -EA SilentlyContinue
  if (-not $rule) {
    New-NetFirewallRule -DisplayName "MC Geyser Bedrock $BedrockPort" -Direction Inbound -Protocol UDP -LocalPort $BedrockPort -Action Allow -Profile Any -EA Stop | Out-Null
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

# Kill old Switch geyser (only this jar; the other Geyser keeps running)
Get-CimInstance Win32_Process -EA SilentlyContinue |
  Where-Object { $_.CommandLine -and ($_.CommandLine -like "*Geyser-Switch-NX.jar*" -or $_.CommandLine -like "*lan_relay.py*" -or $_.CommandLine -like "*ctest_server.py*") } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -EA SilentlyContinue }

# Relay LAN 19132 -> Geyser 19136 y servidor del test de conexión (minimizados; se cierran al parar esta ventana)
$relayProc = Start-Process python -ArgumentList "-P", "`"$Relay`"", "--listen", $BedrockPort, "--target", $GeyserPort -WindowStyle Minimized -PassThru
$ctestProc = Start-Process python -ArgumentList "-P", "`"$Ctest`"", "--port", 80 -WindowStyle Minimized -PassThru

$wifi = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.InterfaceAlias -eq "Wi-Fi" -and $_.IPAddress -like "192.168.*" } | Select-Object -First 1).IPAddress
Write-Host ""
Write-Host "Geyser Switch (Bedrock 26.30)"
Write-Host "  Bedrock : 0.0.0.0:${BedrockPort} (LAN $($wifi):${BedrockPort}) -> relay -> Geyser 127.0.0.1:${GeyserPort}"
Write-Host "  Java    : ${JavaHost}:${JavaPort}"
Write-Host "  Auth    : offline (sin cuenta Microsoft)"
Write-Host "Dejá esta ventana abierta. Ctrl+C para parar."
Write-Host ""

Set-Location $Dir
try {
  & java --enable-native-access=ALL-UNNAMED -Xms256M -Xmx1G -jar $Jar --nogui
} finally {
  Stop-Process -Id $relayProc.Id, $ctestProc.Id -Force -EA SilentlyContinue
}
