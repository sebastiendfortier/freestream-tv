package com.freestream.resolver

import org.junit.Assert.assertTrue
import org.junit.Test

class LevidiaBobResolveTest {
    @Test
    fun bandOfBrothersS1E1HasWootlyHoster() {
        val client = LevidiaResolver.newClient()
        val levidia = LevidiaResolver(client)
        val hosters = levidia.scrape(
            title = "Band of Brothers",
            year = 2001,
            mediaType = "tv",
            season = 1,
            episode = 1,
        )
        assertTrue(
            "expected hosters, got $hosters cookies=${levidia.debugSessionCookieNames()}",
            hosters.isNotEmpty(),
        )
        assertTrue(hosters.any { it.url.contains("wootly", ignoreCase = true) })
        val wootly = WootlyResolver(client)
        val resolved = wootly.resolve(hosters.first { it.url.contains("wootly", true) }.url)
        assertTrue("expected mp4, got $resolved", resolved != null && resolved.streamUrl.startsWith("http"))
    }
}
