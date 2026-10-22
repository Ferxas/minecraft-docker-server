#!/usr/bin/env python3
"""Resize WelcomeAds Oraxen textures to glyph size (256x256). Keeps shift.png 1x1."""
from __future__ import annotations

import struct
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    Image = None  # type: ignore

REPO = Path(__file__).resolve().parents[1]
ASSETS = REPO / "assets"
OUT = REPO / "plugin-configs" / "Oraxen" / "pack" / "textures" / "welcomeads"
TARGET = 256

MAPPING = {
    "wa_page1_raw.png": "welcome_page_1.png",
    "wa_page2_raw.png": "welcome_page_2.png",
    "wa_page3_raw.png": "welcome_page_3.png",
}
# background.png stays official 1×1 overlay (see README); only pages use custom art from assets/


JAR_OFFICIAL = REPO / "plugins" / "WelcomeAds-2.1.jar"
OFFICIAL_SHIFT = "packs/Oraxen/Oraxen/pack/textures/welcomeads/shift.png"


def copy_official_shift(path: Path) -> None:
    import zipfile

    if not JAR_OFFICIAL.is_file():
        raise FileNotFoundError(f"Missing {JAR_OFFICIAL} for official shift.png")
    with zipfile.ZipFile(JAR_OFFICIAL) as zf:
        path.write_bytes(zf.read(OFFICIAL_SHIFT))


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    if Image is None:
        print("Pillow required: pip install Pillow", file=sys.stderr)
        return 1

    for src_name, dst_name in MAPPING.items():
        src = ASSETS / src_name
        dst = OUT / dst_name
        if not src.is_file():
            print(f"skip missing {src}", file=sys.stderr)
            continue
        im = Image.open(src).convert("RGBA")
        im = im.resize((TARGET, TARGET), Image.Resampling.LANCZOS)
        im.save(dst, optimize=True)
        print(f"wrote {dst} ({TARGET}x{TARGET})")

    shift = OUT / "shift.png"
    copy_official_shift(shift)
    print(f"wrote {shift} (official 1x1 from WelcomeAds jar)")
    bg = OUT / "background.png"
    copy_official_shift(bg)
    print(f"wrote {bg} (official 1×1 overlay stub)")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
