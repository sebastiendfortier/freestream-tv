#!/usr/bin/env python3
"""Gate: user-facing strings use media branding, not anime."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRINGS_DIR = ROOT / "app/src/main/res/values"
SCREENS = [
    ROOT / "app/src/main/java/com/freestream/ui/screens/HomeScreen.kt",
    ROOT / "app/src/main/java/com/freestream/ui/screens/SearchScreen.kt",
    ROOT / "app/src/main/java/com/freestream/ui/components/FilterDialog.kt",
]


def main() -> int:
    for xml in STRINGS_DIR.glob("*.xml"):
        if "anime" in xml.read_text(encoding="utf-8").lower():
            print(f"ANIME_STRING_FOUND:{xml.name}")
            return 1

    strings = (STRINGS_DIR / "strings.xml").read_text(encoding="utf-8").lower()
    if "freestream" not in strings:
        print("MISSING_BRAND")
        return 1
    if "movies and tv" not in strings and "movies" not in strings:
        print("MISSING_MEDIA_HINT")
        return 1

    for screen in SCREENS:
        if not screen.is_file():
            print(f"MISSING_SCREEN:{screen.name}")
            return 1
        text = screen.read_text(encoding="utf-8")
        for line_no, line in enumerate(text.splitlines(), 1):
            if "anime" in line.lower() and not line.strip().startswith("//"):
                print(f"ANIME_IN_UI:{screen.name}:{line_no}:{line.strip()}")
                return 1

    print("MEDIA_BRANDING_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
