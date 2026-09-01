#!/usr/bin/env python3
"""Gate: FreeStream branding in app metadata."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRINGS = ROOT / "app/src/main/res/values/strings.xml"
APP = ROOT / "app/src/main/java/com/freestream/FreeStreamApp.kt"
GRADLE = ROOT / "app/build.gradle.kts"


def main() -> int:
  for path in (STRINGS, APP, GRADLE):
    if not path.is_file():
      print(f"MISSING: {path}")
      return 1

  text = STRINGS.read_text(encoding="utf-8") + APP.read_text(encoding="utf-8") + GRADLE.read_text(encoding="utf-8")
  if "com.freestream" not in text:
    print("MISSING_PACKAGE")
    return 1
  if "FreeStream" not in text:
    print("MISSING_BRAND")
    return 1
  print("FREESTREAM_BRANDING_OK")
  return 0


if __name__ == "__main__":
  sys.exit(main())
