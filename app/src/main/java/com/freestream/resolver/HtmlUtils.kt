package com.freestream.resolver

internal fun cleanTitle(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

internal fun stripTags(html: String): String =
    html.replace(Regex("<[^>]+>"), "").trim()

internal fun parseLinks(html: String, className: String, attr: String = "href"): List<String> {
    val pattern = Regex(
        """<a[^>]*class=["'][^"']*\b${Regex.escape(className)}\b[^"']*["'][^>]*$attr=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE,
    )
    return pattern.findAll(html).map { it.groupValues[1] }.toList()
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
    val pattern = Regex(
        """<div[^>]*class=["'][^"']*\bmainlink\b[^"']*["'][^>]*>(.*?)</div>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    return pattern.find(html)?.groupValues?.get(1)
}
