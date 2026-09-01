#!/usr/bin/env python3
"""Copy titles_view.parquet from freestream-database into FreeStream assets."""

from __future__ import annotations

import gzip
import json
import shutil
import sys
from pathlib import Path

import polars as pl

ROOT = Path(__file__).resolve().parent.parent
DB_ROOT = ROOT.parent / "freestream-database"
SOURCE = DB_ROOT / "data" / "titles_view.parquet"
ASSETS = ROOT / "app" / "src" / "main" / "assets"
PARQUET_DEST = ASSETS / "titles_view.parquet"
JSON_DEST = ASSETS / "titles_catalog.json.gz"

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


def map_status(raw: str | None) -> str:
    text = (raw or "").lower()
    if "returning" in text or "production" in text:
        return "airing"
    return "aired"


def export_mobile_json(df: pl.DataFrame) -> None:
    records = []
    for row in df.iter_rows(named=True):
        media = (row.get("media_type") or "movie").upper()
        records.append(
            {
                "title": row["title"],
                "malId": row.get("tmdb_id"),
                "imdbId": row.get("imdb_id") or "",
                "titleRomaji": "",
                "titleJapanese": row.get("original_title") or "",
                "airingStatus": map_status(row.get("status")),
                "type": "MOVIE" if media == "MOVIE" else "TV",
                "year": row.get("year"),
                "scoreMean": float(row.get("imdb_rating") or 0.0),
                "picture": row.get("poster_cdn") or row.get("backdrop_cdn") or "",
                "studios": [],
                "tags": [t.strip() for t in (row.get("genres_csv") or "").split(",") if t.strip()],
                "synopsis": row.get("overview") or "",
            }
        )
    ASSETS.mkdir(parents=True, exist_ok=True)
    with gzip.open(JSON_DEST, "wt", encoding="utf-8") as fh:
        json.dump(records, fh, separators=(",", ":"))


def main() -> int:
    if not SOURCE.is_file():
        print(f"MISSING_SOURCE: {SOURCE}")
        return 1

    df = pl.read_parquet(SOURCE)
    missing = REQUIRED - set(df.columns)
    if missing:
        print(f"MISSING_COLUMNS: {sorted(missing)}")
        return 1

    poster = df.filter(pl.col("poster_cdn").str.starts_with("https://")).height
    if df.height == 0:
        print("EMPTY_CATALOG")
        return 1

    ASSETS.mkdir(parents=True, exist_ok=True)
    shutil.copy2(SOURCE, PARQUET_DEST)
    export_mobile_json(df)

    parquet_mb = PARQUET_DEST.stat().st_size / (1024 * 1024)
    json_mb = JSON_DEST.stat().st_size / (1024 * 1024)
    print(f"CATALOG_SYNC_OK rows={df.height} parquet={parquet_mb:.1f}MB json.gz={json_mb:.1f}MB poster_https={poster}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
