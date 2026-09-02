#!/usr/bin/env python3
"""Static checks for TV resolver fixes."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESOLVER = ROOT / "app/src/main/java/com/freestream/resolver"


def main() -> int:
    levidia = (RESOLVER / "LevidiaResolver.kt").read_text(encoding="utf-8")
    wootly = (RESOLVER / "WootlyResolver.kt").read_text(encoding="utf-8")
    html = (RESOLVER / "HtmlUtils.kt").read_text(encoding="utf-8")

    if "associateBy { it.name }" not in levidia:
        print("TV_RESOLVER_SOURCES_FAIL cookie_merge", file=sys.stderr)
        return 1
    if "tokens.all { cls.contains(it) }" not in html:
        print("TV_RESOLVER_SOURCES_FAIL parse_links_tokens", file=sys.stderr)
        return 1
    if "loadForRequest" not in wootly:
        print("TV_RESOLVER_SOURCES_FAIL wootly_cookies", file=sys.stderr)
        return 1
    if "return null" in wootly and "repeat(10)" in wootly:
        # ensure follow loop does not bail on first non-redirect body
        idx = wootly.find("private fun followToMedia")
        chunk = wootly[idx : idx + 2200]
        if chunk.count("return null") > 1:
            print("TV_RESOLVER_SOURCES_FAIL follow_loop", file=sys.stderr)
            return 1

    print("TV_RESOLVER_SOURCES_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
