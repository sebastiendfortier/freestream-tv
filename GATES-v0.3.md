# Gates: freestream-tv v0.3

OWNS: app/**, scripts/**, GATES-v0.3.md

Scope: MalStream cleanup, media branding, settings API URL, ONN v0.3.0

- [x] G1: No WCO/Otaku dead code
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && python3 scripts/verify_no_wco_refs.py'
  EXPECT: NO_WCO_REFS_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=00db6cba9506/22; out=NO_WCO_REFS_OK

- [x] G2: User-facing strings free of anime branding
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && python3 scripts/verify_media_branding.py'
  EXPECT: MEDIA_BRANDING_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=00db6cba9506/22; out=MEDIA_BRANDING_OK

- [x] G3: StreamResolver wired to database API
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && python3 scripts/verify_stream_resolver_wiring.py'
  EXPECT: STREAM_RESOLVER_WIRING_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=00db6cba9506/22; out=STREAM_RESOLVER_WIRING_OK

- [x] G4: Release APK v0.3.0 builds
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && env JAVA_HOME=$HOME/.local/jdks/temurin-17 PATH=$HOME/.local/jdks/temurin-17/bin:$PATH ./gradlew :app:assembleRelease --no-daemon 2>&1 | python3 -c "import sys; out=sys.stdin.read(); assert \"BUILD SUCCESSFUL\" in out; print(\"BUILD SUCCESSFUL\")"'
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=00db6cba9506/22; out=BUILD SUCCESSFUL

- [x] G5: v0.3.0 installed on ONN
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && adb -s 192.168.18.18:5555 install -r app/build/outputs/apk/release/app-release.apk && adb -s 192.168.18.18:5555 shell dumpsys package com.freestream | python3 -c "import sys; d=sys.stdin.read(); assert \"versionName=0.3.0\" in d; print(\"ONN_V030_INSTALLED\")"'
  EXPECT: ONN_V030_INSTALLED
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=00db6cba9506/22; out=Performing Streamed Install | Success | ONN_V030_INSTALLED
