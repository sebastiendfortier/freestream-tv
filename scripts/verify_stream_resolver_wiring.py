#!/usr/bin/env python3
"""Gate: StreamResolver wired into main play flow."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESOLVER = ROOT / "app/src/main/java/com/freestream/resolver/StreamResolver.kt"
MAIN = ROOT / "app/src/main/java/com/freestream/MainActivity.kt"
DIALOG = ROOT / "app/src/main/java/com/freestream/ui/screens/MediaPlayDialog.kt"


def main() -> int:
    for path in (RESOLVER, MAIN, DIALOG):
        if not path.is_file():
            print(f"MISSING: {path}")
            return 1
    resolver = RESOLVER.read_text(encoding="utf-8")
    main = MAIN.read_text(encoding="utf-8")
    dialog = DIALOG.read_text(encoding="utf-8")
    if "/api/stream/resolve" not in resolver:
        print("MISSING_API_PATH")
        return 1
    if "StreamResolver" not in main or "MediaPlayDialog" not in main:
        print("MISSING_MAIN_WIRING")
        return 1
    if "streamResolver.resolve" not in dialog:
        print("MISSING_DIALOG_RESOLVE")
        return 1
    print("STREAM_RESOLVER_WIRING_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
