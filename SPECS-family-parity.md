# FreeStream ↔ Anime family UX parity

Treat freestream-tv / freestream-database (web) as the same product family as anime-tv / anime-database web. Mirror play/filter/detail UX unless a feature is anime-only.

## Surfaces

| Anime family | FreeStream family | Content | UI target |
|--------------|-------------------|---------|-----------|
| anime-tv | freestream-tv | anime vs movies/TV | Near-identical shell |
| anime database (web) | freestream-database (web) | same | Near-identical shell |

## Shared contracts

1. **Home / filters** — type, score, year, genre include/exclude, search, sort, continue-watching shelf.
2. **Series status filter** — airing / aired(ended) / upcoming from catalog status (TMDB for FreeStream; Otaku/MAL for anime).
3. **Detail / play dialog** — near-fullscreen (TV) or modal (web); left meta (poster, title, genres, synopsis); right seasons + episodes.
4. **Season chips** — selectable seasons; no S+/S− steppers.
5. **Episode list** — scrollable rows with ▶ Play.
6. **Resume / watched** — history map drives Resume CTA, per-row watched/resume labels, progress when duration known.
7. **Continue Watching** — shelf opens detail at saved S/E with highlight when possible.

## FreeStream adaptations (explicit non-goals)

- **No Sub/Dub** chips or audio pills.
- **Genres** = TMDB / FreeStream genres, not Otaku/MAL tags.
- **Resolvers** differ (Levidia/Wootly vs WCO/Otaku); play UX still matches.

## Drift checklist

When anime-tv or anime web changes play/filter/detail UX, update freestream-tv / freestream-database the same way (unless anime-only). Gates:

- `GATES-tv-episode-dialog-parity.md`
- `GATES-web-watch-parity.md`
- `GATES-tv-bob-resolve.md` (stream resolve; FreeStream-only)
