# Gates: TV stream resolve fix (href/class order)

OWNS: app/src/main/java/com/freestream/resolver/HtmlUtils.kt, scripts/simulate_kotlin_levidia.py, GATES-tv-stream-parse-fix.md

Scope: Kotlin Levidia parser finds go.php host links regardless of attribute order

- [ ] G1: Kotlin-equivalent scrape simulation returns hosters for TV and movie fixtures
  CHECK: /bin/sh -c 'python3 /home/slyfox/Documents/freestream-tv/scripts/simulate_kotlin_levidia.py'
  EXPECT: KOTLIN_LEVIDIA_SIM_OK
  EVIDENCE: pending

- [ ] G2: HtmlUtils parseLinks handles href-before-class anchor markup
  CHECK: /bin/sh -c 'python3 /home/slyfox/Documents/freestream-tv/scripts/verify_parse_links_order.py'
  EXPECT: PARSE_LINKS_ORDER_OK
  EVIDENCE: pending
