#!/bin/bash
# Makes the Oraxen pack (WelcomeAds glyphs) actually load on clients:
#  - Oraxen required/* glyph textures (a missing one => "Default font failed to load" => pack discarded)
#  - PackManagerPro config without empty pack-url worlds (those wiped Oraxen's pack)
#  - Skript that re-sends Oraxen's pack after leaving map-pack worlds and logs [PackStatus]
set -euo pipefail
REPO=/mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server
STACK="${STACK:-/home/ferxas/servers/minecraft/stack}"
PLUGINS="$STACK/server-data/plugins"

mkdir -p "$PLUGINS/Oraxen/pack/textures"
cp -rv "$REPO/plugin-configs/Oraxen/pack/textures/required" "$PLUGINS/Oraxen/pack/textures/"
cp -v "$REPO/plugin-configs/PackManagerPro/config.yml" "$PLUGINS/PackManagerPro/config.yml"
cp -v "$REPO/skript/oraxen-pack-return.sk" "$PLUGINS/Skript/scripts/oraxen-pack-return.sk"
chown -R 1000:1000 "$PLUGINS/Oraxen/pack/textures/required"

docker exec mc rcon-cli 'pmp reload'
docker exec mc rcon-cli 'sk reload oraxen-pack-return.sk'
docker exec mc rcon-cli 'oraxen reload all'
echo DONE
