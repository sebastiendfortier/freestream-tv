#!/bin/sh
set -e
cd "$(dirname "$0")/.."
pixi run python scripts/verify_filter_parity.py | grep -q FILTER_PARITY_OK
python3 scripts/verify_freestream_branding.py | grep -q FREESTREAM_BRANDING_OK
pixi run python scripts/verify_parquet_catalog.py | grep -q PARQUET_CATALOG_OK
env JAVA_HOME="$HOME/.local/jdks/temurin-17" PATH="$HOME/.local/jdks/temurin-17/bin:$PATH" ./gradlew :app:assembleRelease --no-daemon 2>&1 | python3 -c "import sys; out=sys.stdin.read(); assert 'BUILD SUCCESSFUL' in out"
echo ALL_MET
