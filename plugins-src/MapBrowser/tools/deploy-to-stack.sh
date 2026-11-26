#!/bin/bash
# Deja el fork de MapBrowser listo en el servidor real (se aplica en el próximo reinicio de mc).
# No toca el JAR que tiene abierto la JVM: lo pone en plugins/update/ y Paper lo cambia al arrancar.
set -euo pipefail
REPO=/mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server
STACK="${STACK:-/home/ferxas/servers/minecraft/stack}"
JAR="$REPO/plugins-src/MapBrowser/build/libs/MapBrowser-1.0-beta3-es1-all.jar"
NAME=MapBrowser-1.0-beta3-all.jar   # nombre de siempre, para que Paper lo reemplace

[ -f "$JAR" ] || { echo "Falta $JAR — compila primero (tools/build-in-wsl.sh)"; exit 1; }

# Jar del repo (el que instala mc-init en una instalación nueva) y copia del repo que usa docker compose
cp -v "$JAR" "$REPO/plugins/$NAME"
cp -v "$JAR" "$STACK/plugins/$NAME"
cp -v "$REPO/scripts/mc-init.sh" "$STACK/scripts/mc-init.sh"
cp -v "$REPO/scripts/deploy-welcomeads.sh" "$STACK/scripts/deploy-welcomeads.sh"
cp -v "$REPO/plugin-configs/MapBrowser/config.yml" "$REPO/plugin-configs/MapBrowser/SETUP.md" "$STACK/plugin-configs/MapBrowser/"
rm -fv "$STACK/skript/mapbrowser-boot.sk" "$STACK/scripts/mapbrowser-restore.sh" "$STACK/plugin-configs/MapBrowser/screens.yml"

# Servidor en marcha
mkdir -p "$STACK/server-data/plugins/update"
cp -v "$JAR" "$STACK/server-data/plugins/update/$NAME"
cp -v "$REPO/plugin-configs/MapBrowser/SETUP.md" "$STACK/server-data/plugins/MapBrowser/SETUP.md"

# El skript que recreaba pantallas tras reiniciar ya sobra (el plugin las guarda solo) y crearía duplicados.
if [ -f "$STACK/server-data/plugins/Skript/scripts/mapbrowser-boot.sk" ]; then
  docker exec mc rcon-cli 'sk disable mapbrowser-boot.sk' || true
  [ -f "$STACK/server-data/plugins/Skript/scripts/mapbrowser-boot.sk" ] && \
    mv -v "$STACK/server-data/plugins/Skript/scripts/mapbrowser-boot.sk" "$STACK/server-data/plugins/Skript/scripts/-mapbrowser-boot.sk"
fi

echo "LISTO: reinicia el servidor (docker restart mc) para cargar MapBrowser 1.0-beta3-es1."
