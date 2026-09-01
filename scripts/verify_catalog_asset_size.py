#!/usr/bin/env python3
"""Gate: parquet asset is materially smaller than legacy sqlite catalog."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PARQUET = ROOT / "app/src/main/assets/anime_view.parquet"
SQLITE = ROOT / "app/src/main/assets/anime.db"
MAX_PARQUET_MB = 8.0


def main() -> int:
    if not PARQUET.is_file():
        print(f"MISSING_PARQUET: {PARQUET}")
        return 1

    parquet_mb = PARQUET.stat().st_size / (1024 * 1024)
    if parquet_mb > MAX_PARQUET_MB:
        print(f"PARQUET_TOO_LARGE: {parquet_mb:.1f}MB")
        return 1

    if SQLITE.is_file():
        sqlite_mb = SQLITE.stat().st_size / (1024 * 1024)
        if parquet_mb >= sqlite_mb:
            print(f"NO_SIZE_WIN: parquet={parquet_mb:.1f}MB sqlite={sqlite_mb:.1f}MB")
            return 1
        print(f"CATALOG_SIZE_OK parquet={parquet_mb:.1f}MB sqlite_was={sqlite_mb:.1f}MB")
    else:
        print(f"CATALOG_SIZE_OK parquet={parquet_mb:.1f}MB sqlite_removed")

    return 0


if __name__ == "__main__":
    sys.exit(main())
