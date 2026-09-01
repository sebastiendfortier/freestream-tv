#!/usr/bin/env python3
"""Gate: each repo has exactly one GitHub release tag."""

from __future__ import annotations

import json
import subprocess
import sys

REPOS = {
    "anime-tv": "sebastiendfortier/anime-tv",
    "anime-database": "sebastiendfortier/anime-database",
}

EXPECTED = {
    "anime-tv": "v1.1.1",
    "anime-database": "v0.4.0",
}


def tags_for(repo: str) -> list[str]:
    out = subprocess.check_output(
        ["gh", "api", f"repos/{repo}/tags", "--jq", ".[].name"],
        text=True,
    )
    return [line.strip() for line in out.splitlines() if line.strip()]


def main() -> int:
    for key, repo in REPOS.items():
        tags = tags_for(repo)
        expected = EXPECTED[key]
        if tags != [expected]:
            print(f"TAG_MISMATCH {key}: {json.dumps(tags)} expected [{expected}]")
            return 1

    print("GITHUB_TAGS_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
