#!/bin/bash
set -euo pipefail
REPO=/mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server
STACK="${STACK:-/home/ferxas/servers/minecraft/stack}"
docker cp mc:/data/libraries/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/paper-api-1.21.11-R0.1-SNAPSHOT.jar /tmp/paper-api.jar
docker cp mc:/data/plugins/WelcomeAds-2.1.jar /tmp/wa.jar
docker cp mc:/data/libraries/net/md-5/bungeecord-chat/1.21-R0.2-deprecated+build.21/bungeecord-chat-1.21-R0.2-deprecated+build.21.jar /tmp/bungeecord-chat.jar
for j in adventure-api adventure-key examination-api examination-api-api; do
  path=$(docker exec mc sh -c "ls /data/libraries/net/kyori/${j}/4.26.1/${j}-4.26.1.jar 2>/dev/null || ls /data/libraries/net/kyori/${j}/*/${j}-*.jar 2>/dev/null | tail -1")
  [ -n "$path" ] && docker cp "mc:$path" "/tmp/${j}.jar"
done
CP="/tmp/paper-api.jar:/tmp/wa.jar:/tmp/bungeecord-chat.jar"
for j in /tmp/adventure-api.jar /tmp/adventure-key.jar /tmp/examination-api.jar; do
  [ -f "$j" ] && CP="$CP:$j"
done
WORKDIR=/tmp/wab2
rm -rf "$WORKDIR"
mkdir -p "$WORKDIR/out" "$WORKDIR/src/welcomeadsbridge"
cp "$REPO/plugins-src/WelcomeAdsBridge/src/welcomeadsbridge/WelcomeAdsBridge.java" "$WORKDIR/src/welcomeadsbridge/"
cp "$REPO/plugins-src/WelcomeAdsBridge/plugin.yml" "$WORKDIR/"
cd "$WORKDIR"
javac -encoding UTF-8 -cp "$CP" -d out src/welcomeadsbridge/WelcomeAdsBridge.java
jar cf WelcomeAdsBridge.jar -C out . plugin.yml
cp WelcomeAdsBridge.jar "$REPO/plugins/"
# Don't overwrite the JAR the running server has open; Paper applies plugins/update/ on restart.
mkdir -p "$STACK/server-data/plugins/update"
cp WelcomeAdsBridge.jar "$STACK/server-data/plugins/update/"
wc -c WelcomeAdsBridge.jar
