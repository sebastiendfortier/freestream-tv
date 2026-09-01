#!/usr/bin/env python3
"""Gate: FreeStream branding is present and legacy Anime TV user strings are gone."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

REQUIRED = [
    (ROOT / "app/src/main/res/values/strings.xml", r"<string name=\"app_name\">FreeStream</string>"),
    (ROOT / "README.md", r"# FreeStream"),
    (ROOT / "app/src/main/java/com/animetv/ui/screens/HomeScreen.kt", r"FreeStream"),
]

FORBIDDEN_USER_FACING = [
    ROOT / "app/src/main/res/values/strings.xml",
    ROOT / "README.md",
    ROOT / "app/src/main/java/com/animetv/ui/screens/HomeScreen.kt",
]


def main() -> int:
    for path, pattern in REQUIRED:
        text = path.read_text(encoding="utf-8")
        if not re.search(pattern, text):
            print(f"MISSING: {path} does not match {pattern!r}")
            return 1

    legacy = re.compile(r"Anime\s+TV", re.IGNORECASE)
    for path in FORBIDDEN_USER_FACING:
        if legacy.search(path.read_text(encoding="utf-8")):
            print(f"LEGACY_BRANDING: {path} still contains 'Anime TV'")
            return 1

    if not (ROOT / "app/src/main/java/com/animetv/FreeStreamApp.kt").is_file():
        print("MISSING: FreeStreamApp.kt")
        return 1

    print("MALSTREAM_BRANDING_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
