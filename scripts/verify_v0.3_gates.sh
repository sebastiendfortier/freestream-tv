#!/bin/sh
set -e
cd "$(dirname "$0")/.."
python3 scripts/verify_no_wco_refs.py | grep -q NO_WCO_REFS_OK
python3 scripts/verify_media_branding.py | grep -q MEDIA_BRANDING_OK
python3 scripts/verify_stream_resolver_wiring.py | grep -q STREAM_RESOLVER_WIRING_OK
env JAVA_HOME="$HOME/.local/jdks/temurin-17" PATH="$HOME/.local/jdks/temurin-17/bin:$PATH" ./gradlew :app:assembleRelease --no-daemon 2>&1 | python3 -c "import sys; out=sys.stdin.read(); assert 'BUILD SUCCESSFUL' in out"
adb -s 192.168.18.18:5555 install -r app/build/outputs/apk/release/app-release.apk | grep -q Success
adb -s 192.168.18.18:5555 shell dumpsys package com.freestream | grep -q versionName=0.3.0
echo ALL_MET
