#!/usr/bin/env python3
"""
Copy WelcomeAds vendor pack layouts into the repo.

Vendor zip layout (TiNYsx / MCModels):
  Oraxen/Oraxen/glyphs/welcomeads/ + pack/textures/welcomeads/
  Oraxen/WelcomeAds/config.yml + inventory.yml
  ItemsAdder/... (reference if you use IA)
  NexO/... (reference if you use NexO)

Active stack here: Oraxen → plugin-configs/Oraxen/ + plugin-configs/WelcomeAds/
"""
from __future__ import annotations

import shutil
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DOWNLOADS = Path.home() / "Downloads"

VENDOR_ORAXEN = DOWNLOADS / "Oraxen"
VENDOR_IA = DOWNLOADS / "ItemsAdder"
VENDOR_NEXO = DOWNLOADS / "NexO"


def copytree(src: Path, dst: Path) -> None:
    if not src.is_dir():
        print(f"skip missing {src}")
        return
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst)
    print(f"copied {src} -> {dst}")


def copy_file(src: Path, dst: Path) -> None:
    if not src.is_file():
        print(f"skip missing {src}")
        return
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    print(f"copied {src.name} -> {dst}")


def main() -> None:
    # --- Oraxen (production path) ---
    vo = VENDOR_ORAXEN / "Oraxen"
    copy_file(
        vo / "glyphs" / "welcomeads" / "welcomeads.yml",
        REPO / "plugin-configs" / "Oraxen" / "glyphs" / "welcomeads" / "welcomeads.yml",
    )
    tex_src = vo / "pack" / "textures" / "welcomeads"
    tex_dst = REPO / "plugin-configs" / "Oraxen" / "pack" / "textures" / "welcomeads"
    tex_dst.mkdir(parents=True, exist_ok=True)
    for png in tex_src.glob("*.png"):
        shutil.copy2(png, tex_dst / png.name)
        print(f"texture {png.name} ({png.stat().st_size} bytes)")

    # WelcomeAds plugin configs (Spanish lives in repo inventory — merge below)
    wa_vendor = VENDOR_ORAXEN / "WelcomeAds"
    packs_oraxen = REPO / "plugin-configs" / "WelcomeAds" / "packs" / "Oraxen"
    copytree(vo, packs_oraxen / "Oraxen")
    copytree(wa_vendor, packs_oraxen / "WelcomeAds")

    # --- ItemsAdder / NexO reference packs (WelcomeAds reload / docs) ---
    if VENDOR_IA.is_dir():
        copytree(VENDOR_IA, REPO / "plugin-configs" / "WelcomeAds" / "packs" / "ItemsAdder")
    if VENDOR_NEXO.is_dir():
        copytree(VENDOR_NEXO, REPO / "plugin-configs" / "WelcomeAds" / "packs" / "NexO")

    print("Done. Hub uses Oraxen paths under plugin-configs/Oraxen/ and WelcomeAds/*.yml")


if __name__ == "__main__":
    main()
