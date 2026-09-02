# Gates: freestream-tv v0.2

OWNS: app/**, scripts/**, GATES-v0.2.md

Scope: Media branding, database stream resolver, ONN play wiring

- [x] G1: FreeStream media branding (no anime strings in UI)
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && python3 scripts/verify_media_branding.py'
  EXPECT: MEDIA_BRANDING_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-tv; exit=0; path=c2fddda5c8ee/25; out=MEDIA_BRANDING_OK

- [x] G2: StreamResolver calls database API
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && python3 scripts/verify_stream_resolver_wiring.py'
  EXPECT: STREAM_RESOLVER_WIRING_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-tv; exit=0; path=c2fddda5c8ee/25; out=STREAM_RESOLVER_WIRING_OK

- [x] G3: Release APK v0.2.0 builds
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && env JAVA_HOME=$HOME/.local/jdks/temurin-17 PATH=$HOME/.local/jdks/temurin-17/bin:$PATH ./gradlew :app:assembleRelease --no-daemon 2>&1 | python3 -c "import sys; out=sys.stdin.read(); assert \"BUILD SUCCESSFUL\" in out; print(\"BUILD SUCCESSFUL\")"'
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-tv; exit=0; path=c2fddda5c8ee/25; out=BUILD SUCCESSFUL

- [x] G4: v0.2.0 installed on ONN
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && adb -s 192.168.18.18:5555 install -r app/build/outputs/apk/release/app-release.apk && adb -s 192.168.18.18:5555 shell dumpsys package com.freestream | python3 -c "import sys; d=sys.stdin.read(); assert \"versionName=0.2.0\" in d; print(\"ONN_V020_INSTALLED\")"'
  EXPECT: ONN_V020_INSTALLED
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-tv; exit=0; path=c2fddda5c8ee/25; out=Performing Streamed Install | Success | ONN_V020_INSTALLED
