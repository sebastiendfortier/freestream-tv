#!/usr/bin/env python3
"""Prove Kotlin grabm path was wrong and root /grabm resolves media."""

from __future__ import annotations

import re
import sys
from urllib.parse import urlencode, urljoin, urlparse

import httpx

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)


def kotlin_grabm(embed_url: str) -> str:
    return embed_url.rsplit("/", 1)[0] + "/grabm"


def python_grabm(embed_url: str) -> str:
    return urljoin(embed_url, "/grabm")


def main() -> int:
    web = "https://www.wootly.ch/?v=FKLAEEE4"
    with httpx.Client(timeout=90.0, follow_redirects=True) as client:
        resp = client.get(web, headers={"User-Agent": UA})
        iframe = re.search(r'<iframe[^>]+src="([^"]+)"', resp.text, re.I)
        if not iframe:
            print("WOOTLY_GRABM_FAIL no_iframe", file=sys.stderr)
            return 1
        embed = iframe.group(1)
        if embed.startswith("//"):
            embed = "https:" + embed

        wrong = kotlin_grabm(embed)
        right = python_grabm(embed)
        if wrong == right:
            print("WOOTLY_GRABM_FAIL paths_unexpectedly_equal", file=sys.stderr)
            return 1

        ref = urljoin(web, "/")
        post_headers = {
            "User-Agent": UA,
            "Referer": web,
            "Origin": f"{urlparse(ref).scheme}://{urlparse(ref).netloc}",
        }
        cookie = "; ".join(f"{k}={v}" for k, v in resp.cookies.items())
        if cookie:
            post_headers["Cookie"] = cookie
        post = client.post(embed, data={"qdfx": 1}, headers=post_headers)
        tk = re.search(r"""tk\s*=\s*["']([^"']+)""", post.text)
        vd = re.search(r"""vd\s*=\s*["']([^"']+)""", post.text)
        if not (tk and vd):
            print("WOOTLY_GRABM_FAIL no_tk_vd", file=sys.stderr)
            return 1

        qs = urlencode({"t": tk.group(1), "id": vd.group(1)})
        bad = client.get(
            f"{wrong}?{qs}",
            headers={"User-Agent": UA, "Referer": ref},
        ).text.strip()
        good = client.get(
            f"{right}?{qs}",
            headers={"User-Agent": UA, "Referer": ref},
        ).text.strip()

        if bad.startswith("http"):
            print("WOOTLY_GRABM_FAIL bad_path_should_404", file=sys.stderr)
            return 1
        if not good.startswith("http"):
            print("WOOTLY_GRABM_FAIL good_path_no_media", file=sys.stderr)
            return 1
        if ".mp4" not in good and "source?" not in good and "http" not in good:
            print("WOOTLY_GRABM_FAIL unexpected_body", file=sys.stderr)
            return 1

    print("WOOTLY_GRABM_OK", f"wrong={wrong}", f"right={right}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
