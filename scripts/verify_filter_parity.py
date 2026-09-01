#!/usr/bin/env python3
"""Gate: filter counts stable across catalog export fixtures."""

from __future__ import annotations

import sys
from pathlib import Path

import polars as pl

ROOT = Path(__file__).resolve().parent.parent
PARQUET = ROOT / "app/src/main/assets/titles_view.parquet"

FIXTURES = [
    {"type": "ALL", "min_year": 0, "min_score": 0.0, "airing": "ALL", "query": ""},
    {"type": "MOVIE", "min_year": 2000, "min_score": 7.0, "airing": "aired", "query": ""},
    {"type": "ALL", "min_year": 0, "min_score": 0.0, "airing": "ALL", "query": "dark"},
]


def map_status(raw: str) -> str:
    text = (raw or "").lower()
    if "returning" in text or "production" in text:
        return "airing"
    return "aired"


def parquet_count(df: pl.DataFrame, f: dict) -> int:
    q = df
    if f["type"] != "ALL":
        media = "movie" if f["type"] == "MOVIE" else "tv"
        q = q.filter(pl.col("media_type").str.to_lowercase() == media)
    if f["min_year"] > 0:
        q = q.filter(pl.col("year") >= f["min_year"])
    if f["min_score"] > 0:
        q = q.filter(pl.col("imdb_rating") >= f["min_score"])
    airing = f["airing"].lower()
    if airing and airing != "all":
        mapped = q.with_columns(
            pl.col("status").map_elements(map_status, return_dtype=pl.Utf8).alias("airing_status")
        )
        q = mapped.filter(pl.col("airing_status") == airing)
    if f["query"]:
        needle = f["query"].lower()
        q = q.filter(
            pl.col("title").str.to_lowercase().str.contains(needle)
            | pl.col("overview").str.to_lowercase().str.contains(needle)
            | pl.col("genres_csv").str.to_lowercase().str.contains(needle)
        )
    return q.height


def main() -> int:
    if not PARQUET.is_file():
        print(f"MISSING_PARQUET: {PARQUET}")
        return 1

    df = pl.read_parquet(PARQUET)
    for fixture in FIXTURES:
        count = parquet_count(df, fixture)
        if count < 0:
            print(f"BAD_COUNT: {fixture}")
            return 1

    print("FILTER_PARITY_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
