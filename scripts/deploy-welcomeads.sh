#!/bin/bash
set -euo pipefail
REPO=/mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server
STACK="${STACK:-/home/ferxas/servers/minecraft/stack}"
# Never overwrite a JAR the running JVM has open (lazy class loading then fails
# with ZipException/NoClassDefFoundError). Stage changed JARs in plugins/update/;
# Paper swaps them in on the next restart.
JARS_STAGED=0
for jar in WelcomeAds-2.1.jar WelcomeAdsBridge.jar; do
  if ! cmp -s "$REPO/plugins/$jar" "$STACK/server-data/plugins/$jar"; then
    mkdir -p "$STACK/server-data/plugins/update"
    cp -v "$REPO/plugins/$jar" "$STACK/server-data/plugins/update/$jar"
    JARS_STAGED=1
  fi
done
for rel in WelcomeAds/config.yml WelcomeAds/container.yml WelcomeAds/inventory.yml WelcomeAds/lang.yml Oraxen/glyphs/welcomeads/welcomeads.yml; do
  mkdir -p "$STACK/server-data/plugins/$(dirname "$rel")"
  cp -v "$REPO/plugin-configs/$rel" "$STACK/server-data/plugins/$rel"
done
mkdir -p "$STACK/server-data/plugins/Oraxen/pack/textures/welcomeads"
cp -v "$REPO/plugin-configs/Oraxen/pack/textures/welcomeads/"*.png "$STACK/server-data/plugins/Oraxen/pack/textures/welcomeads/"
mkdir -p "$STACK/server-data/plugins/WelcomeAds/packs"
cp -a "$REPO/plugin-configs/WelcomeAds/packs/." "$STACK/server-data/plugins/WelcomeAds/packs/"
mkdir -p "$STACK/server-data/plugins/Oraxen/pack/textures/required"
cp -v "$REPO/plugin-configs/Oraxen/pack/textures/required/"*.png "$STACK/server-data/plugins/Oraxen/pack/textures/required/" 2>/dev/null || true
cp -rv "$REPO/plugin-configs/Oraxen/pack/textures/required/" "$STACK/server-data/plugins/Oraxen/pack/textures/" 2>/dev/null || true
cp -v "$REPO/skript/welcomeads-nav.sk" "$STACK/server-data/plugins/Skript/scripts/welcomeads-nav.sk"
cp -v "$REPO/skript/lobby-minigames-item.sk" "$STACK/server-data/plugins/Skript/scripts/lobby-minigames-item.sk"
cp -v "$REPO/skript/oraxen-pack-return.sk" "$STACK/server-data/plugins/Skript/scripts/oraxen-pack-return.sk"
cp -v "$REPO/skript/mapbrowser-boot.sk" "$STACK/server-data/plugins/Skript/scripts/mapbrowser-boot.sk"
python3 "$REPO/scripts/oraxen-restore-hub-items.py" || true
docker exec mc rcon-cli 'sk reload welcomeads-nav.sk'
docker exec mc rcon-cli 'sk reload lobby-minigames-item.sk'
docker exec mc rcon-cli 'sk reload oraxen-pack-return.sk'
docker exec mc rcon-cli 'sk reload mapbrowser-boot.sk'
docker exec mc rcon-cli 'oraxen reload all'
docker exec mc rcon-cli 'welcomeads reload'
docker exec mc rcon-cli 'lp group default permission set welcomeads.font true'
docker exec mc rcon-cli 'lp group default permission set welcomeads.open true'
if [ "$JARS_STAGED" = 1 ]; then
  echo "NOTE: plugin JARs changed — staged in plugins/update/, restart the server to apply (docker restart mc)."
fi
echo DONE
