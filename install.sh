#!/usr/bin/env bash
#
# Instala y arranca el servidor Minecraft completo con un solo comando.
#
# Uso:
#   ./install.sh
#   ./install.sh --fresh                 # plugins + mapas, sin datos de jugadores/MySQL
#   ./install.sh --skip-data-download    # si ya tienes server-data/
#   ./install.sh --import-archive /path/to/server-data.7z.001
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DATA="$ROOT/server-data"
MYSQL_DATA="$ROOT/mysql-data"
DOWNLOAD_DIR="$ROOT/bootstrap/download"
RELEASE_REPO="Ferxas/minecraft-docker-server"
RELEASE_TAG="server-data-v1"
SKIP_DATA_DOWNLOAD=0
FRESH=0
IMPORT_ARCHIVE=""

usage() {
  cat <<'EOF'
Uso:
  ./install.sh
  ./install.sh --fresh                 # plugins + mapas, sin jugadores/MySQL/permisos
  ./install.sh --skip-data-download
  ./install.sh --fresh --skip-data-download
  ./install.sh --import-archive /path/to/server-data.7z.001
EOF
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fresh) FRESH=1; shift ;;
    --skip-data-download) SKIP_DATA_DOWNLOAD=1; shift ;;
    --import-archive)
      [[ $# -lt 2 ]] && { echo "Falta ruta para --import-archive" >&2; exit 1; }
      IMPORT_ARCHIVE="$2"
      shift 2
      ;;
    --release-repo)
      [[ $# -lt 2 ]] && { echo "Falta valor para --release-repo" >&2; exit 1; }
      RELEASE_REPO="$2"
      shift 2
      ;;
    --release-tag)
      [[ $# -lt 2 ]] && { echo "Falta valor para --release-tag" >&2; exit 1; }
      RELEASE_TAG="$2"
      shift 2
      ;;
    -h|--help) usage 0 ;;
    *) echo "Opción desconocida: $1" >&2; usage 1 ;;
  esac
done

server_data_ready() {
  [[ -f "$SERVER_DATA/eula.txt" ]] || return 1
  [[ -d "$SERVER_DATA/plugins" ]] || return 1
  compgen -G "$SERVER_DATA/plugins/"'*.jar' >/dev/null
}

ensure_docker() {
  command -v docker >/dev/null 2>&1 || {
    echo "Docker no está instalado. Instala Docker Engine: https://docs.docker.com/engine/install/" >&2
    exit 1
  }
  # Daemon up but user not in group `docker` → permission denied (misleading if we only say "not running").
  local err
  if err="$(docker info 2>&1)"; then
    return 0
  fi
  if echo "$err" | grep -qiE 'permission denied|connect: permission|Got permission denied'; then
    echo "Docker está corriendo, pero tu usuario no tiene permiso para usarlo." >&2
    echo "Agregate al grupo docker y reabre la terminal:" >&2
    echo "  sudo usermod -aG docker \"\$USER\"" >&2
    echo "  newgrp docker" >&2
    echo "Luego: ./install.sh" >&2
    echo "Atajo: sudo ./install.sh" >&2
    exit 1
  fi
  echo "Docker no está corriendo. Inicia el servicio e inténtalo de nuevo:" >&2
  echo "  sudo systemctl start docker" >&2
  exit 1
}

# Compose v2 (plugin) o docker-compose v1 — Kali/Debian a menudo solo tienen el binario clásico.
docker_compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    cat >&2 <<'EOF'
Docker Compose no encontrado.

En Kali/Debian instala uno de estos:
  sudo apt update
  sudo apt install docker-compose-plugin    # recomendado (docker compose)
  # o:
  sudo apt install docker-compose           # clásico (docker-compose)

Luego vuelve a ejecutar ./install.sh --skip-data-download
EOF
    exit 1
  fi
}

find_7z() {
  if command -v 7z >/dev/null 2>&1; then
    command -v 7z
    return 0
  fi
  if command -v 7za >/dev/null 2>&1; then
    command -v 7za
    return 0
  fi
  if command -v 7zr >/dev/null 2>&1; then
    command -v 7zr
    return 0
  fi
  return 1
}

import_archive_parts() {
  local first_part="$1"
  local seven_zip
  seven_zip="$(find_7z)" || {
    echo "7-Zip no encontrado. Instala p7zip-full (Debian/Ubuntu) o p7zip (Arch) o usa --skip-data-download." >&2
    exit 1
  }
  echo "Extrayendo $first_part ..."
  "$seven_zip" x "$first_part" -o"$ROOT" -y
}

download_release_data() {
  command -v gh >/dev/null 2>&1 || {
    cat >&2 <<EOF
Faltan los datos del servidor (server-data/ + mysql-data/).

Opciones:
  1) Instala GitHub CLI y ejecuta de nuevo: https://cli.github.com/
  2) Descarga manualmente el release '$RELEASE_TAG' de $RELEASE_REPO y ejecuta:
     ./install.sh --import-archive /ruta/server-data.7z.001
  3) Copia las carpetas server-data/ y mysql-data/ junto al repo.
EOF
    exit 1
  }
  mkdir -p "$DOWNLOAD_DIR"
  echo "Descargando datos del servidor desde GitHub Release $RELEASE_TAG ..."
  gh release download "$RELEASE_TAG" --repo "$RELEASE_REPO" --dir "$DOWNLOAD_DIR"
  local first
  first="$(find "$DOWNLOAD_DIR" -maxdepth 1 -name 'server-data*.7z.001' -print -quit)"
  [[ -n "$first" ]] || { echo "No se encontró server-data*.7z.001 en el release." >&2; exit 1; }
  import_archive_parts "$first"
}

echo "=== Con Los Pibes — Minecraft Server ==="
ensure_docker

if [[ -n "$IMPORT_ARCHIVE" ]]; then
  [[ -f "$IMPORT_ARCHIVE" ]] || { echo "Archivo no encontrado: $IMPORT_ARCHIVE" >&2; exit 1; }
  import_archive_parts "$(realpath "$IMPORT_ARCHIVE")"
elif [[ "$SKIP_DATA_DOWNLOAD" -eq 0 ]] && ! server_data_ready; then
  download_release_data
fi

if ! server_data_ready; then
  echo "server-data/ incompleto. Usa --import-archive o publica/descarga el release de datos." >&2
  exit 1
fi

if [[ "$FRESH" -eq 1 ]]; then
  bash "$ROOT/scripts/strip-server-runtime-data.sh" "$ROOT"
fi

mkdir -p "$MYSQL_DATA"

cd "$ROOT"
echo "Iniciando stack (mc-init + mysql + paper) ..."
docker_compose up -d

cat <<'EOF'

Listo.
  Java:    localhost:25565
  Bedrock: localhost:19132 (Geyser)
  Logs:    docker logs mc -f

EOF

if [[ "$FRESH" -eq 1 ]]; then
  cat <<'EOF'
Modo fresh: MySQL, LuckPerms y datos de jugadores están vacíos.
  Reconfigura permisos (/lp editor) y DiscordSRV si lo usas.

EOF
fi
