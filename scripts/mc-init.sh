#!/bin/sh
# Idempotent bootstrap for itzg/minecraft-server /data.
# - Never overwrites existing server files.
# - Seeds from bootstrap/full-server.tar.gz only if /data is empty.
# - Adds missing JARs, configs, skripts and worlds from repo mounts.
set -eu

DATA=/data
BOOT=/bootstrap

log() { printf '[mc-init] %s\n' "$*"; }

ensure_ownership() {
  if [ -d "$DATA" ]; then
    chown -R 1000:1000 "$DATA" 2>/dev/null || true
  fi
}

is_data_empty() {
  if [ ! -f "$DATA/eula.txt" ]; then
    return 0
  fi
  if [ ! -d "$DATA/plugins" ] || [ -z "$(ls -A "$DATA/plugins" 2>/dev/null)" ]; then
    return 0
  fi
  return 1
}

copy_if_missing_file() {
  src=$1
  dest=$2
  if [ ! -f "$dest" ]; then
    mkdir -p "$(dirname "$dest")"
    cp "$src" "$dest"
    log "  + ${dest#$DATA/}"
  fi
}

sync_file() {
  src=$1
  dest=$2
  mkdir -p "$(dirname "$dest")"
  if [ ! -f "$dest" ] || ! cmp -s "$src" "$dest"; then
    cp "$src" "$dest"
    log "  ~ ${dest#$DATA/}"
  fi
}

log "Starting bootstrap..."

mkdir -p "$DATA"

# Full archive seed (for new machines: place bootstrap/full-server.tar.gz with /data contents at tar root).
if is_data_empty; then
  if [ -f "$BOOT/extras/full-server.tar.gz" ]; then
    log "Extracting full server seed (first run only)..."
    tar -xzf "$BOOT/extras/full-server.tar.gz" -C "$DATA"
    ensure_ownership
  elif [ -f "$BOOT/full-server.tar.gz" ]; then
    log "Extracting full server seed (first run only)..."
    tar -xzf "$BOOT/full-server.tar.gz" -C "$DATA"
    ensure_ownership
  else
    log "No full seed archive found; expecting bind-mounted server-data or fresh Paper setup."
  fi
else
  log "Server data already present — skipping full seed."
fi

# Plugin JARs from repo (plugins/*.jar)
if [ -d "$BOOT/plugins" ]; then
  log "Checking plugin JARs..."
  mkdir -p "$DATA/plugins"
  for jar in "$BOOT/plugins"/*.jar; do
    [ -f "$jar" ] || continue
    base=$(basename "$jar")
    if [ ! -f "$DATA/plugins/$base" ]; then
      cp "$jar" "$DATA/plugins/$base"
      log "  + plugins/$base"
    fi
  done
fi

# Plugin configs: plugin-configs/<Plugin>/... -> plugins/<Plugin>/...
if [ -d "$BOOT/plugin-configs" ]; then
  log "Checking plugin configs (new files only)..."
  find "$BOOT/plugin-configs" -type f ! -name '*.txt' | while read -r src; do
    rel=${src#"$BOOT/plugin-configs/"}
    dest="$DATA/plugins/$rel"
    copy_if_missing_file "$src" "$dest"
  done
fi

# Skript scripts (always sync from repo on mc-init)
if [ -d "$BOOT/skript" ]; then
  log "Syncing Skript scripts from repo..."
  mkdir -p "$DATA/plugins/Skript/scripts"
  for sk in "$BOOT/skript"/*.sk; do
    [ -f "$sk" ] || continue
    base=$(basename "$sk")
    sync_file "$sk" "$DATA/plugins/Skript/scripts/$base"
  done
fi

# Plugin configs managed in repo (always sync on mc-init)
for rel in \
  Multiverse-Inventories/groups.yml \
  ; do
  src="$BOOT/plugin-configs/$rel"
  dest="$DATA/plugins/$rel"
  if [ -f "$src" ]; then
    sync_file "$src" "$dest"
  fi
done

# Worlds from repo (folder name with spaces -> underscores in /data)
if [ -d "$BOOT/worlds" ]; then
  log "Checking repo worlds..."
  for worlddir in "$BOOT/worlds"/*; do
    [ -d "$worlddir" ] || continue
    srcname=$(basename "$worlddir")
    destname=$(echo "$srcname" | tr ' ' '_')
    if [ ! -d "$DATA/$destname" ]; then
      log "  + world $srcname -> $destname"
      cp -a "$worlddir" "$DATA/$destname"
    fi
  done
fi

# server.properties template
if [ -f "$BOOT/configs/server.properties" ]; then
  copy_if_missing_file "$BOOT/configs/server.properties" "$DATA/server.properties"
fi

# connection-throttle fix for Bedrock/tunnel (only if bukkit.yml missing)
if [ ! -f "$DATA/bukkit.yml" ] && [ -f "$BOOT/configs/bukkit.yml" ]; then
  cp "$BOOT/configs/bukkit.yml" "$DATA/bukkit.yml"
  log "  + bukkit.yml (template)"
fi

ensure_ownership
log "Bootstrap complete."
