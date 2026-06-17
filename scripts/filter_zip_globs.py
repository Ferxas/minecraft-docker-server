#!/usr/bin/env python3
"""Copy a ZIP omitting entries whose name matches glob patterns (e.g. strip sounds)."""
from __future__ import annotations

import argparse
import fnmatch
import sys
import zipfile
from pathlib import Path


def main() -> int:
    ap = argparse.ArgumentParser(description="Filter ZIP members by glob patterns.")
    ap.add_argument("input_zip", type=Path)
    ap.add_argument("-o", "--output", type=Path, required=True)
    ap.add_argument(
        "--exclude",
        action="append",
        default=[],
        help="Glob against ZIP entry path (posix). Repeatable. E.g. '*.ogg'",
    )
    args = ap.parse_args()
    if not args.input_zip.is_file():
        print(f"No existe: {args.input_zip}", file=sys.stderr)
        return 1
    if not args.exclude:
        print("Nada que excluir: pasa --exclude '*.ogg'", file=sys.stderr)
        return 1

    def excluded(name: str) -> bool:
        n = name.replace("\\", "/")
        for pat in args.exclude:
            if fnmatch.fnmatch(n, pat):
                return True
            if fnmatch.fnmatch(Path(n).name, pat):
                return True
        return False

    kept = skipped = 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.input_zip, "r") as zin, zipfile.ZipFile(
        args.output, "w", compression=zipfile.ZIP_DEFLATED
    ) as zout:
        for info in zin.infolist():
            if info.is_dir():
                continue
            name = info.filename
            if excluded(name):
                skipped += 1
                continue
            data = zin.read(name)
            ni = zipfile.ZipInfo(filename=name)
            ni.compress_type = zipfile.ZIP_DEFLATED
            zout.writestr(ni, data)
            kept += 1

    sz = args.output.stat().st_size
    print(f"Listo: {args.output.resolve()}")
    print(f"  Entradas: {kept} copiadas, {skipped} omitidas")
    print(f"  Tamaño: {sz / 1024 / 1024:.1f} MiB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
