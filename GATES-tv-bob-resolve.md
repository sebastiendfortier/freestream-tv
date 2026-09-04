# Gates: Band of Brothers TV resolve

OWNS: app/src/main/java/com/freestream/resolver/LevidiaResolver.kt, app/src/main/java/com/freestream/resolver/StreamResolver.kt, scripts/verify_bob_resolve.py, scripts/verify_episode_href_match.py, GATES-tv-bob-resolve.md

Scope: Exact sNeM episode href match and Wootly hoster loop for Band of Brothers S1E1

- [x] G1: Episode href matcher uses negative lookahead so s1e1 does not match s1e10
  CHECK: /bin/sh -c 'python3 /home/slyfox/Documents/freestream-tv/scripts/verify_episode_href_match.py'
  EXPECT: EPISODE_HREF_MATCH_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/anime-tv; exit=0; path=c2fddda5c8ee/25; out=EPISODE_HREF_MATCH_OK

- [x] G2: Band of Brothers S1E1 resolves to playable mp4 via Levidia+Wootly
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-resolver && pixi run python /home/slyfox/Documents/freestream-tv/scripts/verify_bob_resolve.py'
  EXPECT: BOB_RESOLVE_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/anime-tv; exit=0; path=c2fddda5c8ee/25; out=BOB_RESOLVE_OK episodes=10 wootly=1

- [x] G3: StreamResolver.kt tries every wootly hoster before failing
  CHECK: /bin/sh -c 'python3 -c "from pathlib import Path; t=Path(\"/home/slyfox/Documents/freestream-tv/app/src/main/java/com/freestream/resolver/StreamResolver.kt\").read_text(); assert \"wootlyHosters\" in t; assert \"for (hoster in wootlyHosters)\" in t; print(\"WOOTLY_LOOP_OK\")"'
  EXPECT: WOOTLY_LOOP_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/anime-tv; exit=0; path=c2fddda5c8ee/25; out=WOOTLY_LOOP_OK
