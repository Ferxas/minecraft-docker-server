#!/usr/bin/env python3
"""Write SkyWars X arena dat files with Unix line endings (LF)."""
from pathlib import Path

ARENA = Path("/data/plugins/Skywars/arenas/skywars1")

locations = """Cuboid: |
  skywars1,-160,85,-190,10,140,10
Chests: '[]'
Spectators-Spawnpoint: skywars1, -86.5, 123.0, -99.5, 0.0, 0.0
Spawnpoints:
  1: skywars1, -145.5, 112.0, -99.5, 0.0, 0.0
  2: skywars1, -83.5, 114.0, -172.5, 0.0, 0.0
  3: skywars1, -82.5, 115.0, -35.5, 0.0, 0.0
  4: skywars1, -27.5, 114.0, -99.5, 0.0, 0.0
"""

blocks = 'Blocks: "[]"\n'

(ARENA / "locations.dat").write_text(locations, encoding="utf-8", newline="\n")
(ARENA / "blocks.dat").write_text(blocks, encoding="utf-8", newline="\n")
print((ARENA / "locations.dat").read_text())
