#!/usr/bin/env python3
"""Disable all Oraxen custom items on hub so pack.zip validates (WelcomeAds glyphs only)."""
from __future__ import annotations

import os
import shutil
from pathlib import Path

STACK = Path(os.environ.get("STACK", "/home/ferxas/servers/minecraft/stack"))
ITEMS = STACK / "server-data" / "plugins" / "Oraxen" / "items"
DISABLED = ITEMS / "_disabled_for_hub_pack"


def main() -> None:
    if not ITEMS.is_dir():
        raise SystemExit(f"Missing {ITEMS}")
    DISABLED.mkdir(parents=True, exist_ok=True)
    moved = 0
    for yml in sorted(ITEMS.glob("*.yml")):
        dest = DISABLED / yml.name
        if dest.exists():
            continue
        shutil.move(str(yml), str(dest))
        moved += 1
    print(f"Moved {moved} item configs to {DISABLED}")
    print("Glyphs/textures unchanged. Run: oraxen reload all")


if __name__ == "__main__":
    main()
