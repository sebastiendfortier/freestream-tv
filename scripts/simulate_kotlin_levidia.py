#!/usr/bin/env python3
"""Simulate Kotlin LevidiaResolver scrape logic against live site."""

from __future__ import annotations

import re
import sys
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar
import http.client

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
BASE = "https://www.levidia.ch"


def clean_title(value: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", value.lower())).strip()


def strip_tags(html: str) -> str:
    return re.sub(r"<[^>]+>", "", html).strip()


def parse_all_links(html: str) -> list[tuple[str, str]]:
    out = []
    for m in re.finditer(r"""<a[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", html, re.I | re.S):
        out.append((strip_tags(m.group(2)), m.group(1)))
    return out


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


def parse_blank_target(html: str) -> list[str]:
    pattern = re.compile(
        r"""<a[^>]*href=["']([^"']+)["'][^>]*target=["']_blank["']"""
        r"""|<a[^>]*target=["']_blank["'][^>]*href=["']([^"']+)["']""",
        re.I,
    )
    out = []
    for match in pattern.finditer(html):
        href = match.group(1) or match.group(2)
        if "imdb" not in href.lower():
            out.append(href)
    return out


def extract_mainlink(html: str) -> str | None:
    open_m = re.search(r"""<div[^>]*class=["'][^"']*\bmainlink\b[^"']*["'][^>]*>""", html, re.I)
    if not open_m:
        return None
    start = open_m.end()
    depth = 1
    i = start
    while i < len(html) and depth > 0:
        nopen = html.lower().find("<div", i)
        nclose = html.lower().find("</div>", i)
        if nclose == -1:
            break
        if nopen != -1 and nopen < nclose:
            depth += 1
            i = nopen + 4
        else:
            depth -= 1
            if depth == 0:
                return html[start:nclose]
            i = nclose + 6
    return None


class Client:
    def __init__(self) -> None:
        self.cj = CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cj))

    def request(self, url: str, method: str = "GET", referer: str = BASE) -> str:
        req = urllib.request.Request(url, method=method, headers={"User-Agent": UA, "Referer": referer})
        if method == "POST":
            req.data = b""
        with self.opener.open(req, timeout=60) as resp:
            return resp.read().decode("utf-8", errors="replace")

    def cookie_header(self, html: str) -> str:
        cookies = {c.name: c.value for c in self.cj}
        m = re.search(r"""_3chk\(['"](.+?)['"],['"](.+?)['"]\)""", html)
        if m:
            cookies[m.group(1)] = m.group(2)
        return "; ".join(f"{k}={v}" for k, v in cookies.items())

    def resolve_go(self, go_url: str, referer: str, cookie_header: str) -> str | None:
        if "go.php" not in go_url:
            return go_url
        req = urllib.request.Request(
            go_url,
            headers={"User-Agent": UA, "Referer": referer, "Cookie": cookie_header},
            method="GET",
        )
        resp = self.opener.open(req, timeout=60)
        loc = resp.headers.get("Location")
        if loc and resp.status in (301, 302, 303, 307, 308):
            if loc.startswith("//"):
                loc = "https:" + loc
            return loc
        final = resp.geturl()
        return None if "go.php" in final else final


def scrape(title: str, year: int | None, media_type: str, season: int | None, episode: int | None) -> list[tuple[str, str]]:
    c = Client()
    c.request(BASE + "/")
    search = c.request(f"{BASE}/search.php?q={urllib.parse.quote(title)}", method="POST")
    block = extract_mainlink(search)
    if not block:
        return []
    links = parse_all_links(block)
    title_key = clean_title(title)
    match_url = None
    for label, href in links:
        years = re.findall(r"\((\d{4})\)", label)
        if not years:
            continue
        name = re.sub(r"\(\d{4}\)", "", label).strip()
        if clean_title(name) == title_key or clean_title(name).find(title_key) >= 0:
            if year is not None and years[0] != str(year):
                continue
            match_url = href if href.startswith("http") else f"{BASE}/{href.lstrip('/')}"
            break
    if not match_url and links:
        href = links[0][1]
        match_url = href if href.startswith("http") else f"{BASE}/{href.lstrip('/')}"
    if not match_url:
        return []
    page_url = match_url
    if media_type.lower() == "tv" and season is not None:
        page_url = f"{match_url}&s={season}"
    page = c.request(page_url)
    referer = page_url
    if media_type.lower() == "tv" and season is not None and episode is not None:
        seaepi = f"s{season}e{episode}"
        ep_href = next((h for _, h in parse_all_links(page) if seaepi in h.lower()), None)
        if not ep_href:
            return []
        referer = ep_href if ep_href.startswith("http") else f"{BASE}/{ep_href.lstrip('/')}"
        page = c.request(referer, referer=page_url)
    links = parse_links(page, "xxx xflv")
    if not links:
        links = parse_blank_target(page)
    ck = c.cookie_header(page)
    out = []
    for link in links:
        abs_link = link if link.startswith("http") else f"{BASE}/{link.lstrip('/')}"
        final = c.resolve_go(abs_link, referer, ck)
        if final:
            out.append(("host", final))
    return out


def main() -> int:
    cases = [
        ("Band of Brothers", 2001, "tv", 1, 1),
        ("Dune: Part Two", 2024, "movie", None, None),
    ]
    ok = True
    for title, year, mt, s, e in cases:
        hosters = scrape(title, year, mt, s, e)
        print(f"{title}: hosters={len(hosters)}", hosters[:2])
        if not hosters:
            ok = False
    if not ok:
        print("KOTLIN_LEVIDIA_SIM_FAIL", file=sys.stderr)
        return 1
    print("KOTLIN_LEVIDIA_SIM_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
