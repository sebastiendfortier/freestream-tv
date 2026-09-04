# Gates: freestream-tv EpisodeDialog parity

OWNS: app/src/main/java/com/freestream/ui/screens/MediaPlayDialog.kt, MainActivity.kt, SPECS-family-parity.md, GATES-tv-episode-dialog-parity.md

Scope: anime-tv EpisodeDialog layout (no Sub/Dub); season chips; LazyColumn; resume/watched wiring.

- [x] G1: Near-fullscreen Dialog + two-column layout present
  CHECK: /bin/sh -c 'python3 -c "from pathlib import Path; t=Path(\"/home/slyfox/Documents/freestream-tv/app/src/main/java/com/freestream/ui/screens/MediaPlayDialog.kt\").read_text(); assert \"usePlatformDefaultWidth = false\" in t; assert \"weight(0.36f)\" in t; assert \"weight(0.64f)\" in t; assert \"LazyColumn\" in t; assert \"LazyRow\" in t; print(\"DIALOG_LAYOUT_OK\")"'
  EXPECT: DIALOG_LAYOUT_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/anime-tv; exit=0; path=c2fddda5c8ee/25; out=DIALOG_LAYOUT_OK

- [x] G2: Season chips present; S+/S− steppers absent
  CHECK: /bin/sh -c 'python3 -c "from pathlib import Path; t=Path(\"/home/slyfox/Documents/freestream-tv/app/src/main/java/com/freestream/ui/screens/MediaPlayDialog.kt\").read_text(); assert \"Season \$season\" in t; assert \"S+\" not in t and \"S-\" not in t; assert \"listAvailableSeasons\" in t; print(\"SEASON_CHIPS_OK\")"'
  EXPECT: SEASON_CHIPS_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/anime-tv; exit=0; path=c2fddda5c8ee/25; out=SEASON_CHIPS_OK

- [x] G3: Resume CTA + historyMap + watched/progress on rows
  CHECK: /bin/sh -c 'python3 -c "from pathlib import Path; t=Path(\"/home/slyfox/Documents/freestream-tv/app/src/main/java/com/freestream/ui/screens/MediaPlayDialog.kt\").read_text(); assert \"historyMap\" in t; assert \"getSeriesHistory\" in t; assert \"Resume\" in t; assert \"Watched\" in t; assert \"episode-progress\" in t or \"progress\" in t.lower(); print(\"HISTORY_UI_OK\")"'
  EXPECT: HISTORY_UI_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/anime-tv; exit=0; path=c2fddda5c8ee/25; out=HISTORY_UI_OK

- [x] G4: MainActivity passes repository into MediaPlayDialog
  CHECK: /bin/sh -c 'python3 -c "from pathlib import Path; t=Path(\"/home/slyfox/Documents/freestream-tv/app/src/main/java/com/freestream/MainActivity.kt\").read_text(); assert \"repository = repository\" in t or \"repository=repository\" in t; print(\"MAIN_REPO_OK\")"'
  EXPECT: MAIN_REPO_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/anime-tv; exit=0; path=c2fddda5c8ee/25; out=MAIN_REPO_OK
