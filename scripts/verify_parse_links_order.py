#!/usr/bin/env python3
"""Gate: anchor href extraction tolerates attribute order."""

from __future__ import annotations

import re
import sys


def parse_links(html: str, class_name: str) -> list[str]:
    tokens = [t for t in class_name.lower().split() if t]
    pattern = re.compile(
        r"""<a[^>]*href=["']([^"']+)["'][^>]*class=["']([^"']+)["']"""
        r"""|<a[^>]*class=["']([^"']+)["'][^>]*href=["']([^"']+)["']""",
        re.I,
    )
    out: list[str] = []
    for match in pattern.finditer(html):
        if match.group(1):
            href, cls = match.group(1), match.group(2).lower()
        else:
            cls, href = match.group(3).lower(), match.group(4)
        if all(token in cls for token in tokens):
            out.append(href)
    return out


def main() -> int:
    samples = [
        '<a href="/go.php?url=abc" class="kiri xxx xflv">Wootly</a>',
        '<a class="xxx xflv kiri" href="/go.php?url=def">Wootly</a>',
    ]
    for sample in samples:
        links = parse_links(sample, "xxx xflv")
        if not links:
            print("PARSE_LINKS_ORDER_FAIL", sample, file=sys.stderr)
            return 1
    print("PARSE_LINKS_ORDER_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
