package com.freestream.resolver

internal fun cleanTitle(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

internal fun stripTags(html: String): String =
    html.replace(Regex("<[^>]+>"), "").trim()

internal fun parseLinks(html: String, className: String, attr: String = "href"): List<String> {
    val pattern = Regex(
        """<a[^>]*class=["']([^"']+)["'][^>]*$attr=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    val tokens = className.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    return pattern.findAll(html).filter { match ->
        val cls = match.groupValues[1].lowercase()
        tokens.all { cls.contains(it) }
    }.map { it.groupValues[2] }.toList()
}

internal fun parseAllLinks(html: String): List<Pair<String, String>> {
    val pattern = Regex("""<a[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    return pattern.findAll(html).map { stripTags(it.groupValues[2]) to it.groupValues[1] }.toList()
}

internal fun parseSpansContaining(html: String, tokens: List<String>): List<String> {
    val pattern = Regex(
        """<span[^>]*class=["']([^"']+)["'][^>]*>(.*?)</span>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    return pattern.findAll(html).filter { match ->
        val cls = match.groupValues[1].lowercase()
        tokens.all { cls.contains(it) }
    }.map { stripTags(it.groupValues[2]) }.toList()
}

internal fun extractMainlinkBlock(html: String): String? {
    val open = Regex(
        """<div[^>]*class=["'][^"']*\bmainlink\b[^"']*["'][^>]*>""",
        RegexOption.IGNORE_CASE,
    ).find(html) ?: return null
    var depth = 1
    var index = open.range.last + 1
    while (index < html.length && depth > 0) {
        val nextOpen = html.indexOf("<div", index, ignoreCase = true)
        val nextClose = html.indexOf("</div>", index, ignoreCase = true)
        if (nextClose == -1) break
        if (nextOpen != -1 && nextOpen < nextClose) {
            depth += 1
            index = nextOpen + 4
        } else {
            depth -= 1
            if (depth == 0) {
                return html.substring(open.range.last + 1, nextClose)
            }
            index = nextClose + 6
        }
    }
    return null
}
