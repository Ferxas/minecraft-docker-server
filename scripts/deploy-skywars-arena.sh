#!/bin/bash
# Sync SkyWars arena dat + settings from repo and reload plugin (fixes broken locations.dat YAML).
set -euo pipefail
REPO=/mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server
STACK="${STACK:-/home/ferxas/servers/minecraft/stack}"
bash "$REPO/scripts/write-skywars-locations.sh"
ARENA=Skywars/arenas/skywars1
for f in locations.dat blocks.dat settings.yml; do
  src="$REPO/plugin-configs/$ARENA/$f"
  dest="$STACK/server-data/plugins/$ARENA/$f"
  mkdir -p "$(dirname "$dest")"
  cp -v "$src" "$dest"
  docker cp "$src" "mc:/data/plugins/$ARENA/$f"
done
docker exec mc rcon-cli sw reload
docker exec mc rcon-cli sw list
