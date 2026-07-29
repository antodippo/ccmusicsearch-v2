package com.antodippo.ccmusicsearch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.LocalDate

class RelevanceRankerTest {

    private fun result(
        title: String,
        service: SearchService,
        date: String = "2020-01-01",
        popularity: Long? = null
    ) = SearchResult(
        author = "author",
        title = title,
        duration = 120,
        bpm = 0,
        tags = "",
        date = LocalDate.parse(date),
        externalLink = URI.create("https://example.org/$title"),
        license = CCLicense.CC_BY,
        service = service,
        popularity = popularity
    )

    private fun titles(results: List<SearchResult>) = results.map { it.title }

    @Test
    fun testItInterleavesServicesInsteadOfConcatenatingThem() {
        val ranked = RelevanceRanker.rank(
            listOf(
                listOf(result("jam1", SearchService.JAMENDO), result("jam2", SearchService.JAMENDO)),
                listOf(result("mix1", SearchService.CCMIXTER), result("mix2", SearchService.CCMIXTER))
            )
        )

        // Neither service reports popularity here, so equal ranks score equally and the
        // stable sort keeps them in service order.
        assertEquals(listOf("jam1", "mix1", "jam2", "mix2"), titles(ranked))
    }

    @Test
    fun testItIgnoresDateEntirely() {
        val ranked = RelevanceRanker.rank(
            listOf(
                listOf(
                    result("mostRelevantButOldest", SearchService.JAMENDO, date = "1999-01-01"),
                    result("lessRelevantButNewest", SearchService.JAMENDO, date = "2026-01-01")
                )
            )
        )

        assertEquals(listOf("mostRelevantButOldest", "lessRelevantButNewest"), titles(ranked))
    }

    @Test
    fun testPopularityLiftsAResultTheServiceRankedLower() {
        // The archive's second hit is also its most downloaded, while its top hit is its
        // least. That is a five-place popularity gap against a one-place relevance gap, so
        // the second hit takes the lead.
        val ranked = RelevanceRanker.rank(
            listOf(
                listOf(
                    result("topHitButIgnored", SearchService.INTERNETARCHIVE, popularity = 1),
                    result("secondButBeloved", SearchService.INTERNETARCHIVE, popularity = 1000),
                    result("third", SearchService.INTERNETARCHIVE, popularity = 500),
                    result("fourth", SearchService.INTERNETARCHIVE, popularity = 400),
                    result("fifth", SearchService.INTERNETARCHIVE, popularity = 300),
                    result("sixth", SearchService.INTERNETARCHIVE, popularity = 200)
                )
            )
        )

        assertEquals(
            listOf("secondButBeloved", "topHitButIgnored", "third", "fourth", "fifth", "sixth"),
            titles(ranked)
        )
    }

    @Test
    fun testRelevanceStillOutweighsPopularityOverASingleRank() {
        // Popularity nudges, it does not take over: one place of relevance is worth more
        // than one place of popularity, however lopsided the download counts are.
        val ranked = RelevanceRanker.rank(
            listOf(
                listOf(
                    result("barelyPlayed", SearchService.INTERNETARCHIVE, popularity = 1),
                    result("aClassic", SearchService.INTERNETARCHIVE, popularity = 99999)
                )
            )
        )

        assertEquals(listOf("barelyPlayed", "aClassic"), titles(ranked))
    }

    @Test
    fun testAServiceWithoutPopularityKeepsItsOwnOrder() {
        val ranked = RelevanceRanker.rank(
            listOf(
                listOf(
                    result("first", SearchService.JAMENDO),
                    result("second", SearchService.JAMENDO),
                    result("third", SearchService.JAMENDO)
                )
            )
        )

        assertEquals(listOf("first", "second", "third"), titles(ranked))
    }

    @Test
    fun testFreesoundIsWeightedBelowTheMusicServices() {
        // Both are their service's top hit, so only the service weight separates them.
        val ranked = RelevanceRanker.rank(
            listOf(
                listOf(result("sample", SearchService.FREESOUND)),
                listOf(result("song", SearchService.JAMENDO))
            )
        )

        assertEquals(listOf("song", "sample"), titles(ranked))
    }

    @Test
    fun testItHandlesEmptyAndMissingServices() {
        val ranked = RelevanceRanker.rank(
            listOf(emptyList(), listOf(result("only", SearchService.JAMENDO)), emptyList())
        )

        assertEquals(listOf("only"), titles(ranked))
        assertEquals(emptyList<SearchResult>(), RelevanceRanker.rank(emptyList()))
    }
}
