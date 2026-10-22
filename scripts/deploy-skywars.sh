#!/bin/bash
# Deploy SkyWars arena, hub flow skript, DeluxeMenus skywars hub.
set -euo pipefail
REPO=/mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server
STACK="${STACK:-/home/ferxas/servers/minecraft/stack}"
tr -d '\r' < "$REPO/scripts/deploy-skywars-arena.sh" | bash
mkdir -p "$STACK/server-data/plugins/DeluxeMenus/gui_menus"
cp -v "$REPO/plugin-configs/DeluxeMenus/gui_menus/skywars_hub.yml" "$STACK/server-data/plugins/DeluxeMenus/gui_menus/skywars_hub.yml"
cp -v "$REPO/skript/skywars-hub-flow.sk" "$STACK/server-data/plugins/Skript/scripts/skywars-hub-flow.sk"
cp -v "$REPO/skript/skywars-lobby-plates.sk" "$STACK/server-data/plugins/Skript/scripts/skywars-lobby-plates.sk"
cp -v "$REPO/skript/skywars-leave.sk" "$STACK/server-data/plugins/Skript/scripts/skywars-leave.sk"
docker exec mc rcon-cli 'sw worldmanager import skywars1' 2>/dev/null || true
docker exec mc rcon-cli 'dm reload'
docker exec mc rcon-cli 'sk reload skywars-hub-flow.sk'
docker exec mc rcon-cli 'sk reload skywars-lobby-plates.sk'
docker exec mc rcon-cli 'sk reload skywars-leave.sk'
tr -d '\r' < "$REPO/scripts/apply-luckperms-skywars.sh" | bash
echo DONE
