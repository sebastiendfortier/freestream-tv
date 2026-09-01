# Gates: Build, install, and verify FreeStream on ONN

OWNS: app/build.gradle.kts, GATES-build-onn.md

Scope: Release APK installs to ONN at 192.168.18.18:5555

- [x] G1: Release APK builds successfully
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && env JAVA_HOME=$HOME/.local/jdks/temurin-17 PATH=$HOME/.local/jdks/temurin-17/bin:$PATH ./gradlew :app:assembleRelease --no-daemon 2>&1 | python3 -c "import sys; out=sys.stdin.read(); assert \"BUILD SUCCESSFUL\" in out; print(\"BUILD SUCCESSFUL\")"'
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-resolver; exit=0; path=c2fddda5c8ee/25; out=BUILD SUCCESSFUL

- [x] G2: APK installed on ONN device
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && adb -s 192.168.18.18:5555 install -r app/build/outputs/apk/release/app-release.apk'
  EXPECT: Success
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-resolver; exit=0; path=c2fddda5c8ee/25; out=Performing Streamed Install | Success

- [x] G3: Installed package reports version 0.1.0
  CHECK: adb -s 192.168.18.18:5555 shell dumpsys package com.freestream | python3 -c "import sys; data=sys.stdin.read(); assert 'versionName=0.1.0' in data; print('ONN_V010_INSTALLED')"
  EXPECT: ONN_V010_INSTALLED
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-tv; exit=0; path=c2fddda5c8ee/25; out=ONN_V010_INSTALLED
