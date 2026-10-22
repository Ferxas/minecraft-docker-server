#!/usr/bin/env python3
"""Restore Oraxen item YAML moved by oraxen-hub-minimal-items.py."""
from __future__ import annotations

import os
import shutil
from pathlib import Path

STACK = Path(os.environ.get("STACK", "/home/ferxas/servers/minecraft/stack"))
ITEMS = STACK / "server-data" / "plugins" / "Oraxen" / "items"
DISABLED = ITEMS / "_disabled_for_hub_pack"


def main() -> None:
    if not DISABLED.is_dir():
        print("Nothing to restore (_disabled_for_hub_pack missing)")
        return
    n = 0
    for yml in DISABLED.glob("*.yml"):
        dest = ITEMS / yml.name
        if dest.exists():
            continue
        shutil.move(str(yml), str(dest))
        n += 1
        print(f"restored {yml.name}")
    if not any(DISABLED.iterdir()):
        DISABLED.rmdir()
    print(f"Restored {n} Oraxen item configs")


if __name__ == "__main__":
    main()
