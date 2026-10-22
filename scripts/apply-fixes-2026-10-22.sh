#!/bin/bash
# Applies the fixes from 2026-10-22 that need the server STOPPED (run inside WSL Ubuntu):
#   bash /mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server/scripts/apply-fixes-2026-10-22.sh
#
# Nothing is deleted: every replaced file is moved to ~/servers/minecraft/claude-restart-20261022/.
#  1. Plugin updates (verified sha512 from Modrinth, already downloaded to ~/servers/minecraft/updates-20261022):
#     AdvancedReplay 1.8.19 (+ ProtocolLib dev-build 2026-10-06) -> fixes broken /replay
#     EssentialsX 2.22.0 (fixes "unsupported server version"), FAWE 2.16.1, WorldGuardExtraFlagsPlus 4.4.6,
#     SimpleScore 4.3.1, GMusic 2.5.2, GSit 3.7.0, PlaceholderAPI 2.12.3, CoreProtect 24.1
#  2. Patched jars already in plugins/update/ (Paper swaps them on start):
#     BattleRoyaleX (hologram names PGLeaderboard->BRLeaderboard, clashed with PartyGames),
#     PartyGames (null-safe round winner: game hung when all scores tied at 0),
#     FeatherMorph (drops README.md from its datapack), WelcomeAdsBridge (duplicate permission)
#  3. Disables SlashAdditions (DiscordSRV API *example* plugin, legacy) and MythicMobs ExampleDialogs.yml
#  4. Datapacks: Herobrine DP disabled (never loaded: bad pack.mcmeta), healthbars repacked (was nested in
#     a subfolder, never loaded), adds conlospibes_fixes (stubs for loot tables the graves pack references)
#  5. Restores the skywars1 arena from the 2026-10-06 backup (an empty FAWE schematic wiped it on rollback)
#  6. server.properties: function-permission-level=3 (Sleep datapack uses /tick), sync-chunk-writes=true
set -u
H=~/servers/minecraft; SD=$H/stack/server-data; P=$SD/plugins; U=$H/updates-20261022
B=$H/claude-restart-20261022; mkdir -p $B/old-jars $B/datapacks-disabled $B/skywars1 $B/misc
log(){ echo "[apply] $*"; }

docker exec mc rcon-cli "list" | sed 's/\x1b\[[0-9;]*m//g'
docker exec mc rcon-cli "save-all flush" >/dev/null
log "stopping mc"; docker stop -t 180 mc >/dev/null && log "stopped"

swap(){ old="$1"; new="$2"; [ -f "$P/$old" ] && mv "$P/$old" $B/old-jars/; cp "$U/$new" "$P/$new"; log "jar $old -> $new"; }
swap AdvancedReplay.jar Replay.jar
swap EssentialsX-2.21.2.jar EssentialsX-2.22.0.jar
swap FastAsyncWorldEdit-Paper-2.15.0.jar FastAsyncWorldEdit-Bukkit-2.16.1.jar
swap worldguard-extraflagsplus-4.3.11.jar worldguard-extraflagsplus-4.4.6.jar
swap SimpleScore-4.1.0.jar SimpleScore-4.3.1.jar
swap GMusic-2.2.2.jar GMusic-2.5.2.jar
swap GSit-3.3.3.jar GSit-3.7.0.jar
swap PlaceholderAPI-2.11.7.jar PlaceholderAPI-2.12.3.jar
swap CoreProtect-CE-23.1.jar CoreProtect-CE-24.1.jar
cp $P/ProtocolLib.jar $B/old-jars/ProtocolLib.jar; cp $U/ProtocolLib.jar $P/update/ProtocolLib.jar; log "ProtocolLib staged"
for j in BattleRoyaleX-4.5.jar PartyGames-7.0.jar feathermorph-2.8.1-1.21.11-final.jar WelcomeAdsBridge.jar; do cp $P/$j $B/old-jars/; done

mkdir -p $P/_disabled_jars
[ -f $P/SlashAdditions.jar ] && mv $P/SlashAdditions.jar $P/_disabled_jars/ && log "SlashAdditions disabled"
[ -f $P/MythicMobs/dialogs/ExampleDialogs.yml ] && mv $P/MythicMobs/dialogs/ExampleDialogs.yml $B/misc/ && log "ExampleDialogs moved out"

DP=$SD/world/datapacks
[ -f "$DP/Herobrine_DP_1.21.10_v6.5.zip" ] && mv "$DP/Herobrine_DP_1.21.10_v6.5.zip" $B/datapacks-disabled/ && log "Herobrine DP disabled"
[ -f $H/staging-datapacks/healthbars-e3080.zip ] && mv "$DP/healthbars-e3080.zip" $B/datapacks-disabled/healthbars-e3080.orig.zip && cp $H/staging-datapacks/healthbars-e3080.zip "$DP/" && log "healthbars repacked"
cp -r $H/staging-datapacks/conlospibes_fixes "$DP/" && log "conlospibes_fixes added"

if [ -d $H/restore-skywars1/backup/minecraft-data/skywars1/region ]; then
  mv $SD/skywars1/region $B/skywars1/region-wiped
  [ -d $SD/skywars1/entities ] && mv $SD/skywars1/entities $B/skywars1/entities-wiped
  cp -a $H/restore-skywars1/backup/minecraft-data/skywars1/region $SD/skywars1/region
  [ -d $H/restore-skywars1/backup/minecraft-data/skywars1/entities ] && cp -a $H/restore-skywars1/backup/minecraft-data/skywars1/entities $SD/skywars1/entities
  [ -f $P/Skywars/arenas/skywars1/save.schem ] && mv $P/Skywars/arenas/skywars1/save.schem $B/skywars1/save.schem.empty
  log "skywars1 restored"
fi

cp $SD/server.properties $B/misc/
sed -i -E 's/^function-permission-level=.*/function-permission-level=3/; s/^sync-chunk-writes=.*/sync-chunk-writes=true/' $SD/server.properties

log "starting mc"; docker start mc >/dev/null
log "done - wait for 'Done' in: docker logs -f mc   (cold start 5-7 min)"
log "then, in-game as OP: /sw updateregion skywars1   (regenerates the arena schematic)"
