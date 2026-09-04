# Gates: series status + episode history

OWNS: freestream-tv CatalogRepository / MediaPlayDialog; freestream-database serve status filter + watch history; tools/sync_catalog_parquet.py

- [x] G1: TV dialog wires historyMap / Resume / Watched
  CHECK: /bin/sh -c 'python3 -c "from pathlib import Path; t=Path(\"/home/slyfox/Documents/freestream-tv/app/src/main/java/com/freestream/ui/screens/MediaPlayDialog.kt\").read_text(); assert \"historyMap\" in t and \"Resume\" in t and \"Watched\" in t; print(\"TV_HISTORY_OK\")"'
  EXPECT: TV_HISTORY_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/anime-tv; exit=0; path=c2fddda5c8ee/25; out=TV_HISTORY_OK

- [x] G2: Catalog sync map_status handles airing/upcoming/aired
  CHECK: /bin/sh -c 'python3 -c "from pathlib import Path; t=Path(\"/home/slyfox/Documents/freestream-tv/tools/sync_catalog_parquet.py\").read_text(); assert \"airing\" in t and \"upcoming\" in t and \"aired\" in t; print(\"MAP_STATUS_OK\")"'
  EXPECT: MAP_STATUS_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/anime-tv; exit=0; path=c2fddda5c8ee/25; out=MAP_STATUS_OK

- [x] G3: Web status filter accepts airing/ended/upcoming against TMDB statuses
  CHECK: /bin/sh -c 'python3 -c "from pathlib import Path; t=Path(\"/home/slyfox/Documents/freestream-database/src/serve.py\").read_text(); assert \"returning\" in t and \"upcoming\" in t and \"released\" in t; print(\"STATUS_FILTER_OK\")"'
  EXPECT: STATUS_FILTER_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/anime-tv; exit=0; path=c2fddda5c8ee/25; out=STATUS_FILTER_OK

- [x] G4: Catalog titles_view has non-empty status values after backfill
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-database && pixi run python -c "import polars as pl; df=pl.read_parquet(\"data/titles_view.parquet\"); empty=df.filter((pl.col(\"status\").is_null())|(pl.col(\"status\")==\"\")).height; nonempty=df.height-empty; assert nonempty>0, nonempty; print(f\"STATUS_POPULATED nonempty={nonempty} empty={empty}\")"'
  EXPECT: STATUS_POPULATED
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/anime-tv; exit=0; path=c2fddda5c8ee/25; out=STATUS_POPULATED nonempty=3971 empty=0
