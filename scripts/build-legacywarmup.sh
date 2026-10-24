#!/bin/bash
set -euo pipefail
REPO=/mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server
STACK="${STACK:-/home/ferxas/servers/minecraft/stack}"
docker cp mc:/data/libraries/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/paper-api-1.21.11-R0.1-SNAPSHOT.jar /tmp/paper-api.jar
docker cp mc:/data/libraries/net/md-5/bungeecord-chat/1.21-R0.2-deprecated+build.21/bungeecord-chat-1.21-R0.2-deprecated+build.21.jar /tmp/bungeecord-chat.jar
for j in adventure-api adventure-key examination-api examination-api-api; do
  path=$(docker exec mc sh -c "ls /data/libraries/net/kyori/${j}/4.26.1/${j}-4.26.1.jar 2>/dev/null || ls /data/libraries/net/kyori/${j}/*/${j}-*.jar 2>/dev/null | tail -1")
  [ -n "$path" ] && docker cp "mc:$path" "/tmp/${j}.jar"
done
CP="/tmp/paper-api.jar:/tmp/bungeecord-chat.jar"
for j in /tmp/adventure-api.jar /tmp/adventure-key.jar /tmp/examination-api.jar; do
  [ -f "$j" ] && CP="$CP:$j"
done
WORKDIR=/tmp/lwb
rm -rf "$WORKDIR"
mkdir -p "$WORKDIR/out" "$WORKDIR/src/legacywarmup"
cp "$REPO/plugins-src/LegacyWarmup/src/legacywarmup/LegacyWarmup.java" "$WORKDIR/src/legacywarmup/"
cp "$REPO/plugins-src/LegacyWarmup/plugin.yml" "$WORKDIR/"
cd "$WORKDIR"
javac -encoding UTF-8 -cp "$CP" -d out src/legacywarmup/LegacyWarmup.java
jar cf LegacyWarmup.jar -C out . plugin.yml
cp LegacyWarmup.jar "$REPO/plugins/"
# New plugin: plugins/update/ only replaces existing jars, so it goes straight into plugins/ (loaded on restart).
cp LegacyWarmup.jar "$STACK/server-data/plugins/"
wc -c LegacyWarmup.jar
