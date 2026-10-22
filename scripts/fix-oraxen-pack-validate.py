#!/usr/bin/env python3
"""Comment out Oraxen item blocks known to break pack validation on live stack."""
from __future__ import annotations

import os
from pathlib import Path

STACK = Path(os.environ.get("STACK", "/home/ferxas/servers/minecraft/stack"))
ITEMS = STACK / "server-data" / "plugins" / "Oraxen" / "items"


def comment_block(lines: list[str], key_prefix: str) -> list[str]:
    out: list[str] = []
    skip = False
    for line in lines:
        if line.startswith(key_prefix):
            skip = True
            out.append("# disabled (broken pack): " + line)
            continue
        if skip:
            if line and not line[0].isspace() and not line.startswith("#"):
                skip = False
            else:
                out.append("# " + line if line.strip() else line)
                continue
        out.append(line)
    return out


def main() -> None:
    cryst = ITEMS / "crystalmush.yml"
    disabled = ITEMS / "crystalmush.yml.disabled-pack"
    if cryst.is_file() and not disabled.is_file():
        cryst.rename(disabled)
        print(f"disabled {cryst.name}")

    for name, prefixes in [
        ("items.yml", ("welcome_disk:",)),
        (
            "armors.yml",
            ("ruby_helmet:", "ruby_chestplate:", "ruby_leggings:", "ruby_boots:"),
        ),
    ]:
        p = ITEMS / name
        if not p.is_file():
            continue
        lines = p.read_text(encoding="utf-8").splitlines(True)
        for pref in prefixes:
            lines = comment_block(lines, pref)
        p.write_text("".join(lines), encoding="utf-8")
        print(f"patched {p}")

    pack_models = STACK / "server-data" / "plugins" / "Oraxen" / "pack" / "assets" / "minecraft" / "models"
    if pack_models.is_dir():
        removed = 0
        for j in pack_models.rglob("*.json"):
            j.unlink()
            removed += 1
        print(f"Removed {removed} stale model JSON under {pack_models} (Oraxen will regenerate on reload)")


if __name__ == "__main__":
    main()
