package com.freestream.resolver

import com.freestream.data.model.SeriesMatch

object MatchRanker {

    private const val DEFAULT_THRESHOLD = 75

    fun titleCandidates(
        displayTitle: String,
        titleRomaji: String? = null,
        titleJapanese: String? = null,
    ): List<String> {
        val seen = mutableSetOf<String>()
        val ordered = mutableListOf<String>()
        for (candidate in listOf(displayTitle, titleRomaji, titleJapanese)) {
            val text = candidate?.trim().orEmpty()
            if (text.isEmpty()) continue
            val key = text.lowercase()
            if (key in seen) continue
            seen.add(key)
            ordered.add(text)
        }
        return ordered
    }

    fun scoreMatch(candidateTitle: String, queryTitle: String): Int {
        val queryTokens = tokenize(queryTitle)
        val candidateTokens = tokenize(candidateTitle)
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return 0

        val intersection = queryTokens.intersect(candidateTokens).size
        val union = queryTokens.union(candidateTokens).size
        val tokenSetRatio = if (union == 0) 0 else (200 * intersection) / union

        val partial = if (queryTokens.size <= candidateTokens.size) {
            val sortedQuery = queryTokens.sorted().joinToString(" ")
            val sortedCandidate = candidateTokens.sorted().joinToString(" ")
            if (sortedCandidate.contains(sortedQuery, ignoreCase = true)) 90 else 0
        } else {
            0
        }

        return maxOf(tokenSetRatio, partial)
    }

    fun rankSeriesMatches(
        matches: List<SeriesMatch>,
        queryTitle: String,
        threshold: Int = DEFAULT_THRESHOLD,
    ): List<SeriesMatch> {
        if (matches.isEmpty() || queryTitle.isBlank()) return matches
        return matches
            .map { match -> match to scoreMatch(match.title, queryTitle) }
            .filter { (_, score) -> score >= threshold }
            .sortedByDescending { (_, score) -> score }
            .map { (match, _) -> match }
    }

    private fun tokenize(text: String): Set<String> {
        return text
            .lowercase()
            .replace(Regex("""[^\w\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
            .toSet()
    }
}
