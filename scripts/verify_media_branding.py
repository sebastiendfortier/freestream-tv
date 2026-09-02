#!/usr/bin/env python3
"""Gate: UI strings use media branding, not anime."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
DIALOG = ROOT / "app/src/main/java/com/freestream/ui/screens/MediaPlayDialog.kt"


def main() -> int:
    strings = STRINGS.read_text(encoding="utf-8").lower()
    if "anime" in strings:
        print("ANIME_STRING_FOUND")
        return 1
    if "freestream" not in strings:
        print("MISSING_BRAND")
        return 1
    if "movies and tv" not in strings:
        print("MISSING_MEDIA_HINT")
        return 1
    if not DIALOG.is_file():
        print("MISSING_DIALOG")
        return 1
    print("MEDIA_BRANDING_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
