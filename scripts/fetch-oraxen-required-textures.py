"""Fetch Oraxen's stock glyph textures (required/*, default/chat/*) into plugin-configs/Oraxen/pack/textures.

A single missing bitmap in minecraft:default makes 1.20+ clients drop the whole font
("Default font failed to load") and discard every server pack, which kills WelcomeAds glyphs.
"""
import pathlib
import struct
import sys
import urllib.request

BASE = "https://raw.githubusercontent.com/oraxen/oraxen/master/src/main/resources/pack/textures/"
TEXTURES = [
    "required/exit_icon",
    "required/logo",
    "required/recipe_showcase",
    "required/recipe_stonecutter",
    "required/ui/menu_banner",
    "required/ui/menu_items",
    "required/ui/menu_items_gems",
    "default/chat/explorer_tag",
    "default/chat/farmer_tag",
    "default/chat/god_tag",
    "default/chat/heart",
    "default/chat/hero_tag",
    "default/chat/king_tag",
    "default/chat/knight_tag",
    "default/chat/lord_tag",
    "default/chat/prince_tag",
]
# Oraxen substitutes required/exit_icon for any other missing glyph texture, so only these must exist;
# the rest are gone upstream and are fetched opportunistically.
CRITICAL = {"required/exit_icon", "required/recipe_stonecutter"}
PNG_SIG = b"\x89PNG\r\n\x1a\n"

out_root = pathlib.Path(__file__).resolve().parent.parent / "plugin-configs" / "Oraxen" / "pack" / "textures"
failed = []


def report(rel, reason):
    if rel in CRITICAL:
        failed.append(f"{rel}: {reason}")
    else:
        print(f"skip {rel}: {reason}")


for rel in TEXTURES:
    url = BASE + rel + ".png"
    try:
        with urllib.request.urlopen(url, timeout=30) as r:
            data = r.read()
    except Exception as e:
        report(rel, e)
        continue
    if not data.startswith(PNG_SIG):
        report(rel, f"not a PNG ({len(data)} bytes)")
        continue
    w, h = struct.unpack(">II", data[16:24])
    dest = out_root / (rel + ".png")
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    print(f"ok  {rel}.png  {w}x{h}  {len(data)} B")

if failed:
    print("FAILED:\n  " + "\n  ".join(failed))
    sys.exit(1)
