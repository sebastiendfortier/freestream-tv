package com.freestream.resolver

import android.util.Base64
import java.net.URI

data class ParsedEpisodeInfo(
    val showTitle: String,
    val seasonNumber: String?,
    val episodeNumber: String?,
    val multipart: String?,
    val episodeTitle: String,
    val cleanLabel: String
)

object WcoDecoder {

    fun unescapeHtml(text: String): String {
        var clean = text
        val replacements = mapOf(
            "&#8216;" to "‘",
            "&#8217;" to "’",
            "&#8211;" to "–",
            "&#8220;" to "“",
            "&#8221;" to "”",
            "&#8230;" to "…",
            "&nbsp;" to " ",
            "&amp;" to "&",
            "&quot;" to "\"",
            "&#039;" to "'",
            "&lt;" to "<",
            "&gt;" to ">"
        )
        replacements.forEach { (old, new) -> clean = clean.replace(old, new) }
        return clean.trim()
    }

    private val ordMap = mapOf(
        "first" to "1",
        "second" to "2",
        "third" to "3",
        "fourth" to "4",
        "fifth" to "5",
        "sixth" to "6",
        "1st" to "1",
        "2nd" to "2",
        "3rd" to "3",
        "4th" to "4",
        "5th" to "5",
        "final" to "Final"
    )

    fun parseEpisodeInfo(rawTitle: String): ParsedEpisodeInfo {
        val title = unescapeHtml(rawTitle).trim()
        var season: String? = null
        var episode: String? = null
        var multipart: String? = null
        var showTitle = title
        var episodeTitle = ""

        // Check for Season patterns: "Season 2", "Season 2nd", "3rd Season", "Final Season"
        val sRegex = Regex("""(?:^|\s)Season\s+(\d+|first|second|third|fourth|fifth|sixth|1st|2nd|3rd|4th|5th|final)""", RegexOption.IGNORE_CASE)
        val sMatch = sRegex.find(title)
        if (sMatch != null) {
            val sVal = sMatch.groupValues[1].lowercase()
            season = ordMap[sVal] ?: if (sVal.all { it.isDigit() }) sVal else "1"
            showTitle = title.substring(0, sMatch.range.first).trim(' ', '-', '–', ':')
        } else {
            val sAltRegex = Regex("""(?:^|\s)(\d+(?:st|nd|rd|th)?|first|second|third|fourth|fifth|sixth|final)\s+Season""", RegexOption.IGNORE_CASE)
            val sAltMatch = sAltRegex.find(title)
            if (sAltMatch != null) {
                val sVal = sAltMatch.groupValues[1].lowercase()
                val digits = sVal.filter { it.isDigit() }
                season = ordMap[sVal] ?: digits.ifEmpty { "1" }
                showTitle = title.substring(0, sAltMatch.range.first).trim(' ', '-', '–', ':')
            }
        }

        // Episode parsing: "Episode 05" or "Episode 05-06"
        val epRegex = Regex("""(?:^|\s)Episode\s+(\d+)(?:-(\d+))?""", RegexOption.IGNORE_CASE)
        val epMatch = epRegex.find(title)
        if (epMatch != null) {
            episode = epMatch.groupValues[1]
            if (epMatch.groupValues.size > 2 && epMatch.groupValues[2].isNotEmpty()) {
                multipart = epMatch.groupValues[2]
            }
            if (sMatch == null) {
                showTitle = title.substring(0, epMatch.range.first).trim(' ', '-', '–', ':')
            }
            if (season == null) {
                season = "1"
            }

            val afterEp = title.substring(epMatch.range.last + 1).trim(' ', '-', '–', ':')
            val engIdx = afterEp.indexOf("English", ignoreCase = true)
            episodeTitle = if (engIdx != -1) {
                afterEp.substring(0, engIdx).trim(' ', '-', '–', ':')
            } else {
                afterEp
            }
        }

        val cleanLabel = buildString {
            if (season != null && season != "1") {
                if (season.equals("Final", ignoreCase = true)) {
                    append("Final Season • ")
                } else {
                    append("S$season • ")
                }
            }
            if (episode != null) {
                val epPadded = if (episode.length == 1) "0$episode" else episode
                append("Episode $epPadded")
                if (multipart != null) append("-$multipart")
            } else {
                // If movie or OVA or special
                val cleanWithoutAudio = title
                    .replace(Regex("""\s*English\s+(?:Subbed|Dubbed).*""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s*\((?:Sub|Dub)\).*""", RegexOption.IGNORE_CASE), "")
                    .trim()
                append(cleanWithoutAudio.ifEmpty { title })
            }
            if (episodeTitle.isNotEmpty() && episodeTitle != title) {
                append(": $episodeTitle")
            }
        }

        return ParsedEpisodeInfo(
            showTitle = showTitle.trim(' ', '-'),
            seasonNumber = season,
            episodeNumber = episode,
            multipart = multipart,
            episodeTitle = episodeTitle.trim(' ', '/'),
            cleanLabel = cleanLabel
        )
    }

    fun decodeIframeSource(content: String, baseUrl: String): String? {
        val directPatterns = listOf(
            Regex("""<iframe\s*id=['"][a-zA-Z]+uploads\d+['"]\s*src=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""<iframe\s*(?:rel=['"]nofollow['"])?\s*id=['"][a-zA-Z]+-js-\d+['"]\s*src=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""<iframe[^>]+src=['"](https?://(?:embed|vhs)\.wcostream\.com[^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""<iframe[^>]+src=['"](https?://[^'"]+/inc/embed/[^'"]+)['"]""", RegexOption.IGNORE_CASE)
        )

        for (pattern in directPatterns) {
            val match = pattern.find(content)
            if (match != null) {
                var src = match.groupValues[1].trim()
                if (src.startsWith("//")) {
                    src = "https:$src"
                }
                return src
            }
        }

        var embedIdx = content.indexOf("onclick=\"myFunction")
        if (embedIdx == -1) embedIdx = content.indexOf("var _0x")
        if (embedIdx == -1) embedIdx = content.indexOf("document.write(")
        if (embedIdx == -1) embedIdx = 0

        val subContent = content.substring(embedIdx)
        val leftBracket = subContent.indexOf("[")
        val rightBracket = if (leftBracket != -1) subContent.indexOf("]", leftBracket) else -1
        if (leftBracket == -1 || rightBracket == -1) {
            return null
        }

        val charsStr = subContent.substring(leftBracket + 1, rightBracket)
        val spreadRegex = Regex("""-\s*(\d+)\s*\)\s*;""")
        var spreadMatch = spreadRegex.find(subContent)
        if (spreadMatch == null) {
            spreadMatch = Regex("""-\s*(\d+)""").find(subContent.substring(rightBracket))
        }
        val spread = spreadMatch?.groupValues?.get(1)?.toIntOrNull() ?: return null

        val tokens = charsStr.split(",").map { it.trim().trim('"', '\'') }.filter { it.isNotEmpty() }
        val decodedBuilder = StringBuilder()

        for (token in tokens) {
            try {
                val rawBytes = Base64.decode(token, Base64.DEFAULT)
                val rawString = String(rawBytes, Charsets.UTF_8)
                val digitsOnly = rawString.filter { it.isDigit() }
                if (digitsOnly.isNotEmpty()) {
                    val charCode = digitsOnly.toInt() - spread
                    if (charCode in 1..65535) {
                        decodedBuilder.append(charCode.toChar())
                    }
                }
            } catch (_: Exception) {
                continue
            }
        }

        val iframeHtml = decodedBuilder.toString()
        if (iframeHtml.isEmpty()) return null

        val srcRegex = Regex("""src=["']([^"']+)""")
        val srcMatch = srcRegex.find(iframeHtml) ?: return null
        var embedSrc = srcMatch.groupValues[1].trim()
        if (embedSrc.startsWith("//")) {
            return "https:$embedSrc"
        }
        if (embedSrc.startsWith("http://") || embedSrc.startsWith("https://")) {
            return embedSrc
        }
        return URI(baseUrl).resolve(embedSrc).toString()
    }
}
