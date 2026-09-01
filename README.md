# FreeStream TV

Android TV client (MalStream fork) for movies and TV. Package `com.freestream` v0.1.0.

## Build

```bash
pixi run sync-catalog   # from freestream-database titles_view.parquet
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Verify

```bash
pixi run verify-parquet
python3 scripts/verify_filter_parity.py
```
