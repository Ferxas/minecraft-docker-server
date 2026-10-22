#!/bin/bash
# Write locations.dat with Unix LF (Windows CRLF breaks some YAML parsers).
python3 - <<'PY'
from pathlib import Path
repo = Path("/mnt/c/Users/Usuario/Documents/Minecraft/minecraft-docker-server")
text = """Cuboid: "skywars1, -160, 85, -190, 10, 140, 10"
Chests: '[]'
Spectators-Spawnpoint: skywars1, -86.5, 123.0, -99.5, 0.0, 0.0
Spawnpoints:
  1: skywars1, -145.5, 112.0, -99.5, 0.0, 0.0
  2: skywars1, -83.5, 114.0, -172.5, 0.0, 0.0
  3: skywars1, -82.5, 115.0, -35.5, 0.0, 0.0
  4: skywars1, -27.5, 114.0, -99.5, 0.0, 0.0
"""
out = repo / "plugin-configs/Skywars/arenas/skywars1/locations.dat"
out.write_text(text, encoding="utf-8", newline="\n")
print("wrote", out, "bytes", out.stat().st_size)
PY
