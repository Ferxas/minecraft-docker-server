#!/usr/bin/env python3
"""
Fusiona dos resource packs de Minecraft (ZIP): primero la BASE, encima el OVERLAY
(los archivos del segundo pisan al primero en rutas repetidas).

Después puedes subir el ZIP con la API de mcpacks.dev:
  https://mcpacks.dev/docs/api
POST multipart field name: file

Requisitos: solo biblioteca estándar. Subida opcional vía curl (incluido en Windows 10+).
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

BLOCKED_FOR_MCPACKS = {".exe", ".jar", ".bat", ".cmd", ".msi"}
API_DEFAULT = "https://mcpacks.dev/api/v1/packs"


def _extract(zip_path: Path, dest: Path) -> None:
    dest.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(dest)


def _copy_tree_merge(src: Path, dst: Path) -> None:
    """Copia src sobre dst; archivos existentes se sobrescriben."""
    if not src.exists():
        return
    for path in src.rglob("*"):
        if path.is_dir():
            continue
        rel = path.relative_to(src)
        out = dst / rel
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, out)


def _pack_format(meta: dict) -> int:
    pack = meta.get("pack") or {}
    pf = pack.get("pack_format")
    if isinstance(pf, int):
        return pf
    return 0


def merge_pack_mcmeta(base_meta: Path, overlay_meta: Path, out_meta: Path) -> None:
    def load(p: Path) -> dict:
        if not p.exists():
            return {}
        return json.loads(p.read_text(encoding="utf-8"))

    b, o = load(base_meta), load(overlay_meta)
    merged: dict = json.loads(json.dumps(b)) if b else {}

    if not merged and o:
        merged = json.loads(json.dumps(o))
    elif o:
        # Conserva claves de overlay fuera de pack (features, etc.)
        for k, v in o.items():
            if k == "pack":
                continue
            merged[k] = v

    bp = merged.setdefault("pack", {})
    opack = o.get("pack") or {}
    bp["pack_format"] = max(_pack_format(b), _pack_format(o))
    # Descripción: overlay suele ser el mapa; si existe, gana; si no, base.
    if opack.get("description") is not None:
        bp["description"] = opack["description"]
    elif b.get("pack", {}).get("description") is not None:
        bp["description"] = b["pack"]["description"]

    out_meta.parent.mkdir(parents=True, exist_ok=True)
    out_meta.write_text(json.dumps(merged, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def zip_directory_flat(src_dir: Path, out_zip: Path) -> None:
    """Crea ZIP con rutas relativas a src_dir; omite extensiones bloqueadas por mcpacks.dev."""
    skipped = []
    with zipfile.ZipFile(out_zip, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(src_dir.rglob("*")):
            if path.is_dir():
                continue
            suf = path.suffix.lower()
            if suf in BLOCKED_FOR_MCPACKS:
                skipped.append(path.relative_to(src_dir))
                continue
            arc = path.relative_to(src_dir).as_posix()
            zf.write(path, arcname=arc)
    if skipped:
        print("Omitidos (no permitidos en mcpacks.dev):", file=sys.stderr)
        for r in skipped[:30]:
            print(f"  - {r}", file=sys.stderr)
        if len(skipped) > 30:
            print(f"  ... y {len(skipped) - 30} más", file=sys.stderr)


def fetch_url(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "mc-compose-merge-pack/1.0"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        dest.write_bytes(resp.read())


def upload_mcpacks(zip_path: Path, api_url: str) -> dict:
    curl = shutil.which("curl")
    if not curl:
        raise RuntimeError("curl no está en PATH; instálalo o sube el ZIP manualmente.")
    cmd = [
        curl,
        "-s",
        "-S",
        "-X",
        "POST",
        api_url,
        "-F",
        # curl en Windows acepta ruta con barras normales o invertidas
        f"file=@{zip_path.resolve()}",
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"curl falló ({proc.returncode}): stderr={proc.stderr!r} stdout={proc.stdout!r}")
    raw = (proc.stdout or "").strip()
    if not raw:
        raise RuntimeError(f"Respuesta vacía del servidor (curl ok). stderr={proc.stderr!r}")
    try:
        return json.loads(raw)
    except json.JSONDecodeError as e:
        raise RuntimeError(f"No es JSON (¿HTML error?): {e}\nPrimeros 500 chars:\n{raw[:500]}") from e


def main() -> int:
    p = argparse.ArgumentParser(description="Fusiona dos resource packs ZIP para Minecraft.")
    p.add_argument("--base", required=True, help="ZIP base o URL https del pack actual del servidor")
    p.add_argument(
        "--overlay",
        required=True,
        help="ZIP que se superpone, o carpeta del mapa (ej. worlds/.../resources con pack.mcmeta)",
    )
    p.add_argument("-o", "--output", required=True, help="ZIP fusionado de salida")
    p.add_argument("--upload", action="store_true", help="Subir a mcpacks.dev tras fusionar")
    p.add_argument("--api-url", default=API_DEFAULT, help=f"Endpoint POST (default: {API_DEFAULT})")
    args = p.parse_args()

    base_path = Path(args.base)
    overlay_path = Path(args.overlay)
    output_path = Path(args.output)

    with tempfile.TemporaryDirectory() as td:
        t = Path(td)
        base_dir = t / "base"
        overlay_dir = t / "overlay"
        merged_dir = t / "merged"

        if str(args.base).startswith(("http://", "https://")):
            bz = t / "base_download.zip"
            print("Descargando base…")
            fetch_url(args.base, bz)
            _extract(bz, base_dir)
        else:
            if not base_path.is_file():
                print(f"No existe base: {base_path}", file=sys.stderr)
                return 1
            _extract(base_path, base_dir)

        if overlay_path.is_dir():
            overlay_dir = overlay_path.resolve()
            if not (overlay_dir / "pack.mcmeta").is_file():
                print(f"Overlay es carpeta pero falta pack.mcmeta: {overlay_dir}", file=sys.stderr)
                return 1
        else:
            if not overlay_path.is_file():
                print(f"No existe overlay (archivo o carpeta): {overlay_path}", file=sys.stderr)
                return 1
            _extract(overlay_path, overlay_dir)

        merged_dir.mkdir(parents=True, exist_ok=True)
        _copy_tree_merge(base_dir, merged_dir)
        _copy_tree_merge(overlay_dir, merged_dir)

        merge_pack_mcmeta(
            base_dir / "pack.mcmeta",
            overlay_dir / "pack.mcmeta",
            merged_dir / "pack.mcmeta",
        )

        if not (merged_dir / "pack.mcmeta").exists():
            print("Error: falta pack.mcmeta en el resultado.", file=sys.stderr)
            return 1

        output_path.parent.mkdir(parents=True, exist_ok=True)
        print("Creando ZIP fusionado…")
        zip_directory_flat(merged_dir, output_path)

    print(f"Listo: {output_path.resolve()} ({output_path.stat().st_size // 1024 // 1024} MiB aprox.)")

    if args.upload:
        print("Subiendo a mcpacks.dev …")
        data = upload_mcpacks(output_path, args.api_url.rstrip("/"))
        print(json.dumps(data, indent=2, ensure_ascii=False))
        if not data.get("success"):
            print("La API respondió sin éxito.", file=sys.stderr)
            return 1
        sp = data.get("data", {}).get("server_properties", {})
        print("\nCopiar en server.properties:")
        print(f"resource-pack={sp.get('resource-pack', '')}")
        print(f"resource-pack-sha1={sp.get('resource-pack-sha1', '')}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
