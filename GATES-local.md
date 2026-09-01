# Gates: freestream-tv local

OWNS: app/**, tools/**, scripts/**, GATES-local.md

Scope: Android TV app fork from MalStream with titles catalog

- [x] G1: Filter parity script passes
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && pixi run python scripts/verify_filter_parity.py'
  EXPECT: FILTER_PARITY_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-resolver; exit=0; path=c2fddda5c8ee/25; out=FILTER_PARITY_OK

- [x] G2: FreeStream branding verified
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && python3 scripts/verify_freestream_branding.py'
  EXPECT: FREESTREAM_BRANDING_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-resolver; exit=0; path=c2fddda5c8ee/25; out=FREESTREAM_BRANDING_OK

- [x] G3: Bundled titles parquet valid
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && pixi run python scripts/verify_parquet_catalog.py'
  EXPECT: PARQUET_CATALOG_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-resolver; exit=0; path=c2fddda5c8ee/25; out=PARQUET_CATALOG_OK

- [x] G4: Release APK builds
  CHECK: /bin/sh -c 'cd /home/slyfox/Documents/freestream-tv && env JAVA_HOME=$HOME/.local/jdks/temurin-17 PATH=$HOME/.local/jdks/temurin-17/bin:$PATH ./gradlew :app:assembleRelease --no-daemon 2>&1 | python3 -c "import sys; out=sys.stdin.read(); assert \"BUILD SUCCESSFUL\" in out; print(\"BUILD SUCCESSFUL\")"'
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-resolver; exit=0; path=c2fddda5c8ee/25; out=BUILD SUCCESSFUL
