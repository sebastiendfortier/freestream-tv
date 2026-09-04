package com.freestream.resolver

import org.junit.Assert.assertTrue
import org.junit.Test

class LevidiaSeveranceResolveTest {
    @Test
    fun severanceS1E1HasWootlyHoster() {
        val client = LevidiaResolver.newClient()
        val levidia = LevidiaResolver(client)
        val seasons = (1..3).filter { levidia.listEpisodes("Severance", 2022, it).isNotEmpty() }
        println("SEASONS=" + seasons)
        val eps = levidia.listEpisodes("Severance", 2022, 1)
        println("EPS=" + eps)
        assertTrue("expected episodes", eps.isNotEmpty())
        val hosters = levidia.scrape(
            title = "Severance",
            year = 2022,
            mediaType = "tv",
            season = 1,
            episode = 1,
            episodeUrl = eps.first().episodeUrl,
        )
        println("HOSTERS=" + hosters)
        assertTrue("expected hosters, got $hosters", hosters.isNotEmpty())
        val wootly = WootlyResolver(client)
        val resolved = wootly.resolve(hosters.first { it.url.contains("wootly", true) }.url)
        println("RESOLVED=" + resolved)
        assertTrue(resolved != null && resolved!!.streamUrl.startsWith("http"))
    }
}
