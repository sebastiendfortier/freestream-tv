#!/usr/bin/env python3
"""Gate: Band of Brothers S1E1 scrapes hosters and Wootly resolves to mp4."""

from __future__ import annotations

import re
import sys

import httpx

from freestream_resolver.hosters.wootly import resolve_wootly
from freestream_resolver.models import ScrapeRequest
from freestream_resolver.sites.levidia import LevidiaScraper


def episode_href_exact(links: list[str], season: int, episode: int) -> str | None:
    pattern = re.compile(rf"s{season}e{episode}(?!\d)", re.I)
    for href in links:
        if pattern.search(href):
            return href
    return None


def main() -> int:
    req = ScrapeRequest(
        imdb_id="tt0185906",
        title="Band of Brothers",
        year=2001,
        media_type="tv",
        season=1,
        episode=1,
    )
    scraper = LevidiaScraper()
    try:
        candidates = scraper.scrape(req)
        episodes = scraper.list_episodes(req)
    finally:
        scraper.close()

    if len(episodes) < 2:
        print("BOB_RESOLVE_FAIL few_episodes", len(episodes), file=sys.stderr)
        return 1

    # Prove exact match preference on synthetic ordering.
    synthetic = [
        "tv-episode.php?watch=x-s1e10-y",
        "tv-episode.php?watch=x-s1e1-currahee",
    ]
    if episode_href_exact(synthetic, 1, 1) != synthetic[1]:
        print("BOB_RESOLVE_FAIL href_match", file=sys.stderr)
        return 1

    wootly = [c for c in candidates if "wootly" in c.url.lower()]
    if not wootly:
        print("BOB_RESOLVE_FAIL no_wootly", file=sys.stderr)
        return 1

    with httpx.Client(timeout=90.0, follow_redirects=True) as client:
        resolved = None
        for cand in wootly:
            resolved = resolve_wootly(client, cand.url)
            if resolved and ".mp4" in resolved.stream_url.lower():
                break
            resolved = None
    if not resolved:
        print("BOB_RESOLVE_FAIL no_mp4", file=sys.stderr)
        return 1

    print(
        "BOB_RESOLVE_OK",
        f"episodes={len(episodes)}",
        f"wootly={len(wootly)}",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
