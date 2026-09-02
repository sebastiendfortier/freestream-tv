#!/usr/bin/env python3
"""Gate: no WCO/Otaku/EpisodeDialog references in Kotlin sources."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java"

PATTERNS = [
    re.compile(r"WcoResolver"),
    re.compile(r"OtakuResolver"),
    re.compile(r"EpisodeDialog"),
    re.compile(r"wcostream", re.I),
]


def main() -> int:
    hits: list[str] = []
    for path in SRC.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for pat in PATTERNS:
            if pat.search(text):
                hits.append(f"{path.relative_to(ROOT)}:{pat.pattern}")
    if hits:
        print("WCO_REFS_FOUND")
        for h in hits:
            print(h)
        return 1
    print("NO_WCO_REFS_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
