#!/bin/bash
# Compila el fork de MapBrowser dentro de WSL (Docker no ve /mnt/c) y deja el jar en build/libs del repo.
set -euo pipefail
SRC=/mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server/plugins-src/MapBrowser
WORK=$HOME/build/mapbrowser
mkdir -p "$WORK"
rsync -a --delete --exclude .git --exclude build --exclude .gradle "$SRC"/ "$WORK"/
cd "$WORK"
rm -rf build/libs
docker run --rm -u "$(id -u):$(id -g)" -e GRADLE_USER_HOME=/cache -v "$WORK":/src -v "$HOME/.cache/mb-gradle":/cache -w /src gradle:8.8-jdk21 gradle shadowJar --no-daemon -q
rm -rf "$SRC/build/libs" && mkdir -p "$SRC/build/libs"
cp -v build/libs/*-all.jar "$SRC/build/libs/"
