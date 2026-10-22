#!/bin/bash
# Apply plugin-configs/luckperms-skywars.txt via rcon (skip comments and blanks).
set -euo pipefail
REPO=/mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server
FILE="$REPO/plugin-configs/luckperms-skywars.txt"
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line//$'\r'/}"
  [[ -z "$line" || "$line" =~ ^# ]] && continue
  echo "LP: $line"
  docker exec mc rcon-cli "$line" || true
  sleep 0.3
done < "$FILE"
echo DONE luckperms-skywars
