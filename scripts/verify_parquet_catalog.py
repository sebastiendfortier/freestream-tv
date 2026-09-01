#!/usr/bin/env python3
"""Gate: bundled titles parquet has required columns."""

from __future__ import annotations

import sys
from pathlib import Path

import polars as pl

ROOT = Path(__file__).resolve().parent.parent
ASSET = ROOT / "app/src/main/assets/titles_view.parquet"

REQUIRED = {
    "tmdb_id",
    "imdb_id",
    "title",
    "media_type",
    "year",
    "imdb_rating",
    "poster_cdn",
    "genres_csv",
    "overview",
    "status",
}


def main() -> int:
    if not ASSET.is_file():
        print(f"MISSING_ASSET: {ASSET}")
        return 1

    schema = set(pl.read_parquet(ASSET, n_rows=0).columns)
    missing = REQUIRED - schema
    if missing:
        print(f"MISSING_COLUMNS: {sorted(missing)}")
        return 1

    df = pl.read_parquet(ASSET, columns=["poster_cdn"])
    if df.height == 0:
        print("EMPTY_CATALOG")
        return 1

    print("PARQUET_CATALOG_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
