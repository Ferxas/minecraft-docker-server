#!/bin/bash
# MapBrowser walls are RAM-only. mb create must run as a real player in SakuraSpawn (see mapbrowser-boot.sk).
set -euo pipefail
echo "MapBrowser restore:"
echo "  1) Join SakuraSpawn as a player with mapbrowser.command.create"
echo "  2) Run: /mapbrowserrestore"
echo "  (mapbrowser-boot.sk also auto-runs when an admin enters SakuraSpawn after restart)"
docker exec mc rcon-cli 'sk reload mapbrowser-boot.sk' 2>/dev/null || true
PLAYERS=$(docker exec mc rcon-cli list 2>/dev/null | grep -i online || true)
echo "Online: $PLAYERS"
