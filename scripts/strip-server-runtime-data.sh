#!/usr/bin/env bash
# Quita datos de runtime (jugadores, MySQL, permisos, etc.) pero conserva
# plugins JAR, configs y mapas/arenas ya construidos.
set -euo pipefail

ROOT="${1:-}"
if [[ -z "$ROOT" ]]; then
  echo "Uso: $0 <ruta-al-repo>" >&2
  exit 1
fi

SERVER_DATA="$ROOT/server-data"
MYSQL_DATA="$ROOT/mysql-data"

log() { printf '[fresh] %s\n' "$*"; }

if [[ ! -d "$SERVER_DATA" ]]; then
  echo "No existe server-data/ en $ROOT" >&2
  exit 1
fi

log "Modo fresh: conservando plugins, configs y mundos; borrando datos de jugadores/DB..."

if [[ -d "$MYSQL_DATA" ]]; then
  log "  mysql-data/"
  find "$MYSQL_DATA" -mindepth 1 -maxdepth 1 -exec rm -rf {} + 2>/dev/null || true
fi
mkdir -p "$MYSQL_DATA"

PLUGINS="$SERVER_DATA/plugins"
if [[ -d "$PLUGINS" ]]; then
  for rel in \
    "LuckPerms/luckperms-h2"*.db* \
    "Essentials/userdata" \
    "Essentials/usermap.csv" \
    "Essentials/uuids.bin" \
    "DiscordSRV/accounts.aof" \
    "CoreProtect/database.db" \
    "Skript/variables.csv" \
    "PlayerPoints/storage.db"; do
    for path in "$PLUGINS"/$rel; do
      [[ -e "$path" ]] || continue
      log "  plugins/${path#$PLUGINS/}"
      rm -rf "$path"
    done
  done
  rm -rf "$PLUGINS/LuckPerms/yaml-storage" 2>/dev/null || true
  mkdir -p "$PLUGINS/LuckPerms/yaml-storage" 2>/dev/null || true
  rm -rf "$PLUGINS/Essentials/userdata" 2>/dev/null || true
  mkdir -p "$PLUGINS/Essentials/userdata" 2>/dev/null || true
  rm -rf "$PLUGINS/AdvancedReplay/replays"/* 2>/dev/null || true
  rm -f "$PLUGINS/DiscordSRV/linked"* 2>/dev/null || true
  rm -f "$PLUGINS/Plan/"*.db "$PLUGINS/Plan/"*.db-journal 2>/dev/null || true
fi

for f in usercache.json banned-players.json banned-ips.json ops.json whitelist.json; do
  if [[ -f "$SERVER_DATA/$f" ]]; then
    log "  $f"
    rm -f "$SERVER_DATA/$f"
  fi
done

shopt -s nullglob
for world in "$SERVER_DATA"/*/; do
  base="$(basename "$world")"
  case "$base" in
    plugins|config|logs|cache|versions|libraries|backups|.cache|bluemap|emotes|iDisguise-Dummy)
      continue
      ;;
  esac
  [[ -f "${world}level.dat" ]] || continue
  log "  mundo $base → playerdata/stats/advancements"
  rm -rf "${world}playerdata" "${world}stats" "${world}advancements"
  rm -rf "${world}data/playerdata" "${world}data/stats" "${world}data/advancements" 2>/dev/null || true
  rm -f "${world}uid.dat" "${world}session.lock"
done

if [[ -d "$SERVER_DATA/logs" ]]; then
  log "  logs/"
  rm -f "$SERVER_DATA/logs/"*.log.gz "$SERVER_DATA/logs/"*.log 2>/dev/null || true
fi

log "Listo. Configura permisos de nuevo con LuckPerms al primer arranque."
