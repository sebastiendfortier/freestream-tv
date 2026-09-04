# Gates: TV Wootly grabm URL fix

OWNS: app/src/main/java/com/freestream/resolver/WootlyResolver.kt, app/src/main/java/com/freestream/player/TvPlayerActivity.kt, scripts/verify_wootly_grabm.py, GATES-tv-wootly-grabm.md, app/build.gradle.kts

Scope: TV Wootly resolver hits /grabm at site root like Python, returning playable mp4

- [x] G1: Kotlin-style grabm path differs from Python until fixed; fixed path returns media URL
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-resolver && pixi run python /home/slyfox/Documents/freestream-tv/scripts/verify_wootly_grabm.py'
  EXPECT: WOOTLY_GRABM_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=c2fddda5c8ee/25; out=WOOTLY_GRABM_OK wrong=https://web.wootly.ch/e/gjZr3OZDwbxd_WrZptSnZA/1788538711/5114270478/grabm right=https://web.wootly.ch/grabm

- [x] G2: WootlyResolver.kt builds grabm at site root encodedPath /grabm
  CHECK: /bin/sh -c 'python3 -c "from pathlib import Path; t=Path(\"/home/slyfox/Documents/freestream-tv/app/src/main/java/com/freestream/resolver/WootlyResolver.kt\").read_text(); assert \"encodedPath(\\\"/grabm\\\")\" in t; assert \"substringBeforeLast\" not in t; print(\"WOOTLY_SOURCE_OK\")"'
  EXPECT: WOOTLY_SOURCE_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=c2fddda5c8ee/25; out=WOOTLY_SOURCE_OK

- [ ] G3: ONN device reports installed version 0.3.5 after redeploy
  CHECK: /bin/sh -c 'adb -s 192.168.18.18:5555 shell dumpsys package com.freestream | python3 -c "import sys; d=sys.stdin.read(); assert \"versionName=0.3.5\" in d; print(\"ONN_V035_INSTALLED\")"'
  EXPECT: ONN_V035_INSTALLED
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=1; path=c2fddda5c8ee/25; out=Traceback (most recent call last): |   File "<string>", line 1, in <module> | AssertionError
