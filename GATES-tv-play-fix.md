# Gates: TV on-device play parity with web

OWNS: app/src/main/java/com/freestream/resolver/**, GATES-tv-play-fix.md, scripts/verify_tv_resolver_parity.py

Scope: TV Levidia/Wootly resolver finds hosters and resolves playable mp4 like freestream-resolver

- [x] G1: Python reference resolves Band of Brothers S1E1 wootly hoster
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-resolver && pixi run python scripts/verify_tv_resolver_parity.py'
  EXPECT: TV_RESOLVER_PARITY_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=c2fddda5c8ee/25; out=TV_RESOLVER_PARITY_OK episodes=10 hosters=3

- [x] G2: Kotlin resolver sources include cookie merge and fixed wootly redirect loop
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && python3 scripts/verify_tv_resolver_sources.py'
  EXPECT: TV_RESOLVER_SOURCES_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=c2fddda5c8ee/25; out=TV_RESOLVER_SOURCES_OK

- [x] G3: Resolver source checks pass after fix
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && python3 scripts/verify_tv_resolver_sources.py'
  EXPECT: TV_RESOLVER_SOURCES_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=c2fddda5c8ee/25; out=TV_RESOLVER_SOURCES_OK
