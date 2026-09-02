# FreeStream TV — standalone playback (no laptop server)

## Gates

| ID | Outcome | Check |
|----|---------|-------|
| G1 | Play resolves on-device | `StreamResolver` uses `LevidiaResolver` + `WootlyResolver` without `remoteBaseUrl` |
| G2 | No default server URL | `strings.xml` `api_base_url` is empty; blank settings = on-device only |
| G3 | TV shows episode list | `MediaPlayDialog` calls `listEpisodes` and renders E1…En buttons |
| G4 | Continue watching opens correct title | Shelf lookup via `getMediaByTitle` + season/episode passed to dialog |
| G5 | Optional remote fallback | Non-blank settings URL falls back to `/api/stream/resolve` when local fails |

## Manual verify (device)

1. Install fresh APK (or clear app data).
2. Settings → remote URL field **empty**.
3. Open a movie → Play → ExoPlayer starts (no laptop server running).
4. Open a TV series → season 1 episode list loads → tap E1 → plays.
5. Continue watching row opens same series at saved S/E.

## Build

```bash
cd freestream-tv && ./gradlew assembleDebug
```
