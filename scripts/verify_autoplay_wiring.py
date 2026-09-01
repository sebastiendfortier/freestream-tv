#!/usr/bin/env python3
"""Gate: auto-play next episode and series-complete return are wired."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLAYER = ROOT / "app/src/main/java/com/animetv/player/TvPlayerActivity.kt"
DIALOG = ROOT / "app/src/main/java/com/animetv/ui/screens/EpisodeDialog.kt"
SESSION = ROOT / "app/src/main/java/com/animetv/data/model/PlaybackSession.kt"


def main() -> int:
  missing: list[str] = []

  if not SESSION.is_file():
    missing.append("PlaybackSession.kt")
  else:
    session_text = SESSION.read_text(encoding="utf-8")
    if "PlaybackSession" not in session_text or "PlaybackEpisode" not in session_text:
      missing.append("PlaybackSession model")

  if not PLAYER.is_file():
    missing.append("TvPlayerActivity.kt")
  else:
    player_text = PLAYER.read_text(encoding="utf-8")
    for token in (
      "EXTRA_SERIES_COMPLETE",
      "userExplicitExit",
      "STATE_ENDED",
      "playEpisodeAt",
    ):
      if token not in player_text:
        missing.append(f"TvPlayerActivity:{token}")

  if not DIALOG.is_file():
    missing.append("EpisodeDialog.kt")
  else:
    dialog_text = DIALOG.read_text(encoding="utf-8")
    for token in (
      "rememberLauncherForActivityResult",
      "EXTRA_SERIES_COMPLETE",
      "PlaybackSession",
    ):
      if token not in dialog_text:
        missing.append(f"EpisodeDialog:{token}")

  if missing:
    print("AUTOPLAY_WIRING_MISSING: " + ", ".join(missing))
    return 1

  print("AUTOPLAY_WIRING_OK")
  return 0


if __name__ == "__main__":
  sys.exit(main())
