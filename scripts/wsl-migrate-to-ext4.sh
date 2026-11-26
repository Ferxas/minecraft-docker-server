#!/usr/bin/env bash
# One-shot migrate: Windows NTFS repo -> WSL ext4 layout
set -euo pipefail

SRC="/mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server"
ROOT="/home/ferxas/servers/minecraft"
STACK="$ROOT/stack"

echo "==> Creating layout under $ROOT"
mkdir -p "$ROOT"/{bin,logs,notes} "$STACK"

echo "==> Syncing project to ext4 (excludes download archives + git junk)"
# rsync if available, else cp -a with find exclusions
if command -v rsync >/dev/null 2>&1; then
  rsync -a --info=progress2 \
    --exclude 'bootstrap/download/' \
    --exclude 'bootstrap/release/' \
    --exclude '.bench-tmp/' \
    --exclude '.bench-io.sh' \
    --exclude '**/session.lock' \
    --exclude '.git/objects/pack/*.tmp' \
    "$SRC/" "$STACK/"
else
  echo "rsync missing; installing..."
  sudo apt-get update -qq && sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq rsync
  rsync -a --info=progress2 \
    --exclude 'bootstrap/download/' \
    --exclude 'bootstrap/release/' \
    --exclude '.bench-tmp/' \
    --exclude '.bench-io.sh' \
    --exclude '**/session.lock' \
    "$SRC/" "$STACK/"
fi

# Ensure shell scripts are LF + executable
find "$STACK" -name '*.sh' -type f -exec sed -i 's/\r$//' {} \;
chmod +x "$STACK"/install.sh "$STACK"/scripts/*.sh 2>/dev/null || true

mkdir -p "$STACK/mc-backups"

# Marker so we know this is the live copy
cat > "$ROOT/ACTIVE" <<EOF
live=stack
path=$STACK
fs=ext4
migrated=$(date -Iseconds)
source_windows=$SRC
EOF

echo "==> Layout:"
du -sh "$STACK" "$STACK/server-data" 2>/dev/null || true
ls -la "$ROOT"
echo "DONE_MIGRATE"
