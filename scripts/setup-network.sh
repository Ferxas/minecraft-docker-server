#!/usr/bin/env bash
# Abre puertos en el firewall del host Linux para Java + Bedrock (Geyser).
#
# Uso:
#   sudo ./scripts/setup-network.sh
#   sudo ./scripts/setup-network.sh --remove
#
set -euo pipefail

REMOVE=0
if [[ "${1:-}" == "--remove" ]]; then
  REMOVE=1
fi

JAVA_RULE="ConLosPibes Minecraft Java"
BEDROCK_RULE="ConLosPibes Geyser Bedrock"

open_ufw() {
  if ! command -v ufw >/dev/null 2>&1; then
    echo "ufw no instalado; instala con: sudo apt install ufw" >&2
    return 1
  fi
  if [[ "$REMOVE" -eq 1 ]]; then
    ufw delete allow 25565/tcp comment "$JAVA_RULE" 2>/dev/null || true
    ufw delete allow 19132/udp comment "$BEDROCK_RULE" 2>/dev/null || true
    echo "Reglas ufw eliminadas (si existian)."
    return 0
  fi
  ufw allow 25565/tcp comment "$JAVA_RULE"
  ufw allow 19132/udp comment "$BEDROCK_RULE"
  echo "ufw: 25565/tcp y 19132/udp permitidos."
}

open_firewalld() {
  if ! command -v firewall-cmd >/dev/null 2>&1; then
    return 1
  fi
  if [[ "$REMOVE" -eq 1 ]]; then
    firewall-cmd --permanent --remove-port=25565/tcp 2>/dev/null || true
    firewall-cmd --permanent --remove-port=19132/udp 2>/dev/null || true
    firewall-cmd --reload
    echo "firewalld: puertos eliminados."
    return 0
  fi
  firewall-cmd --permanent --add-port=25565/tcp
  firewall-cmd --permanent --add-port=19132/udp
  firewall-cmd --reload
  echo "firewalld: 25565/tcp y 19132/udp permitidos."
}

echo "=== Con Los Pibes — firewall (Linux) ==="
if [[ "$(id -u)" -ne 0 ]]; then
  echo "Ejecuta con sudo." >&2
  exit 1
fi

if open_firewalld 2>/dev/null; then
  :
elif open_ufw; then
  :
else
  echo "No se detecto firewalld ni ufw. Abre manualmente 25565/TCP y 19132/UDP." >&2
  exit 1
fi

if [[ "$REMOVE" -eq 0 ]]; then
  echo ""
  echo "Falta reenvio en el router (NAT):"
  echo "  25565/TCP  -> IP local del host (Java)"
  echo "  19132/UDP  -> IP local del host (Bedrock/Geyser)"
  if command -v curl >/dev/null 2>&1; then
    pub=$(curl -fsS --max-time 5 https://api.ipify.org || true)
    [[ -n "$pub" ]] && echo "IP publica Java: ${pub}:25565"
  fi
  echo ""
  echo "Xbox/Bedrock: DNS secundario 104.238.130.180 (BedrockConnect)"
fi
