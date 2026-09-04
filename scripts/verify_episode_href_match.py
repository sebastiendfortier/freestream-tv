#!/usr/bin/env python3
"""Gate: sNeM episode href matching must not confuse s1e1 with s1e10."""

from __future__ import annotations

import re
import sys


def find_episode_href(links: list[str], season: int, episode: int) -> str | None:
    pattern = re.compile(rf"s{season}e{episode}(?!\d)", re.I)
    for href in links:
        if pattern.search(href):
            return href
    return None


def main() -> int:
    links = [
        "tv-episode.php?watch=band-of-brothers-s1e10-points",
        "tv-episode.php?watch=band-of-brothers-s1e1-currahee",
        "tv-episode.php?watch=band-of-brothers-s1e11-why-we-fight",
    ]
    hit = find_episode_href(links, 1, 1)
    if not hit or "s1e1-currahee" not in hit:
        print("EPISODE_HREF_MATCH_FAIL wrong_e1", hit, file=sys.stderr)
        return 1
    # Naive contains would pick s1e10 first — ensure we did not.
    if "s1e10" in hit:
        print("EPISODE_HREF_MATCH_FAIL matched_s1e10", file=sys.stderr)
        return 1
    hit10 = find_episode_href(links, 1, 10)
    if not hit10 or "s1e10" not in hit10:
        print("EPISODE_HREF_MATCH_FAIL missing_e10", hit10, file=sys.stderr)
        return 1
    print("EPISODE_HREF_MATCH_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
