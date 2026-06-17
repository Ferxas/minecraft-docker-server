#!/usr/bin/env python3
"""Print largest members of a ZIP (uncompressed sizes)."""
from __future__ import annotations

import argparse
import os
import zipfile
from collections import defaultdict


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("zip_path")
    ap.add_argument("--top", type=int, default=30)
    args = ap.parse_args()
    p = args.zip_path
    by_ext: dict[str, int] = defaultdict(int)
    items: list[tuple[int, str]] = []
    with zipfile.ZipFile(p) as z:
        for i in z.infolist():
            if i.is_dir():
                continue
            items.append((i.file_size, i.filename))
            ext = i.filename.rsplit(".", 1)[-1].lower() if "." in i.filename else ""
            by_ext[ext] += i.file_size
    items.sort(reverse=True)
    print(f"ZIP: {p}")
    print(f"File size: {os.path.getsize(p) / 1024 / 1024:.1f} MiB\n")
    print(f"Top {args.top} (uncompressed):")
    for sz, name in items[: args.top]:
        print(f"  {sz / 1024 / 1024:8.1f} MiB  {name[:100]}")
    print("\nBy extension (MiB):")
    for ext, sz in sorted(by_ext.items(), key=lambda x: -x[1])[:20]:
        print(f"  {sz / 1024 / 1024:8.1f}  .{ext}")
    tot = sum(s for s, _ in items)
    print(f"\nSum uncompressed: {tot / 1024 / 1024:.1f} MiB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
