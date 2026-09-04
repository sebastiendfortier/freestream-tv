# Gates: TV stream resolve fix (href/class order)

OWNS: app/src/main/java/com/freestream/resolver/HtmlUtils.kt, scripts/simulate_kotlin_levidia.py, GATES-tv-stream-parse-fix.md

Scope: Kotlin Levidia parser finds go.php host links regardless of attribute order

- [x] G1: Kotlin-equivalent scrape simulation returns hosters for TV and movie fixtures
  CHECK: /bin/sh -c 'python3 /home/slyfox/Documents/freestream-tv/scripts/simulate_kotlin_levidia.py'
  EXPECT: KOTLIN_LEVIDIA_SIM_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=c2fddda5c8ee/25; out=Band of Brothers: hosters=3 [('host', 'https://www.wootly.ch/?v=PNW4EEE4'), ('host', 'https://playmogo.com/d/bvvcgd3oql6u')] | Dune: Part Two: hosters=9 [('host', 'https://www.wootly.ch/?v=FKLAEEE4'), ('host', 'https://playmogo.com/d/ohwex8...

- [x] G2: HtmlUtils parseLinks handles href-before-class anchor markup
  CHECK: /bin/sh -c 'python3 /home/slyfox/Documents/freestream-tv/scripts/verify_parse_links_order.py'
  EXPECT: PARSE_LINKS_ORDER_OK
  EVIDENCE: shell=/bin/sh; cwd=/home/slyfox/Documents/freestream-database; exit=0; path=c2fddda5c8ee/25; out=PARSE_LINKS_ORDER_OK
