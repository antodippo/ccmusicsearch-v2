package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatReadsFromFile
import com.antodippo.ccmusicsearch.CCLicense
import com.antodippo.ccmusicsearch.SearchResult
import com.antodippo.ccmusicsearch.SearchService
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatReturnsAnEmptyBody
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatThrows
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.time.LocalDate

@SpringBootTest
class CCMixterTest {

    @Test
    fun testItFetchesAJsonAndReturnsAListOfSearchResults() = runBlocking {
        val ccMixter = CCMixter(ApiClientThatReadsFromFile("ccmixter"))
        val results = ccMixter.search("test")

        val expectedResults = listOf(
            SearchResult(
                author = "Reiswerk",
                title = "Luwan House feat Sonja V",
                duration = 148,
                bpm = 123,
                tags = "dance,pop,house,deep,vocals",
                date = LocalDate.parse("2014-07-01"),
                externalLink = URI.create("http://ccmixter.org/files/Reiswerk/46456"),
                license = CCLicense.CC_BY_NC,
                service = SearchService.CCMIXTER,
                popularity = 6
            ),
            SearchResult(
                author = "JeffSpeed68",
                title = "Procrastinating_in_The_Sun",
                duration = 178,
                bpm = 151,
                tags = "male_vocals,drums,guitar,bass,synthesizer,rock,pop",
                date = LocalDate.parse("2014-06-27"),
                externalLink = URI.create("http://ccmixter.org/files/JeffSpeed68/46416"),
                license = CCLicense.CC_BY,
                service = SearchService.CCMIXTER,
                popularity = 9
            )
        )

        assertEquals(expectedResults, results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientThrowsAnException() = runBlocking {
        val ccMixter = CCMixter(ApiClientThatThrows())
        val results = ccMixter.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyJson() = runBlocking {
        val ccMixter = CCMixter(ApiClientThatReadsFromFile("emptyresponse"))
        val results = ccMixter.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyBody() = runBlocking {
        val ccMixter = CCMixter(ApiClientThatReturnsAnEmptyBody())
        val results = ccMixter.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    // Not every upload is a timed track. Stem packs and sample kits are a single zip, and
    // upload 70960 — captured from a live "profession" search — is only its cover image, so
    // none of them has a playing time. Reading one unguarded threw, and took the other
    // uploads in the response down with it: 47 searches in a fortnight lost ccMixter that way.
    @Test
    fun testItKeepsUploadsWithNoPlayingTimeAndSkipsOnlyTheUnreadableOnes() = runBlocking {
        val ccMixter = CCMixter(ApiClientThatReadsFromFile("ccmixterwithoddmetadata"))
        val results = ccMixter.search("test")

        val expectedResults = listOf(
            SearchResult(
                author = "Reiswerk",
                title = "Luwan House feat Sonja V",
                duration = 148,
                bpm = 123,
                tags = "dance,pop,house,deep,vocals",
                date = LocalDate.parse("2014-07-01"),
                externalLink = URI.create("http://ccmixter.org/files/Reiswerk/46456"),
                license = CCLicense.CC_BY_NC,
                service = SearchService.CCMIXTER,
                popularity = 6
            ),
            // No audio file at all: kept, with the length left unknown.
            SearchResult(
                author = "Puma_Tunes",
                title = "Feel Motivated",
                duration = 0,
                bpm = 112,
                tags = "advertising,ambient,atmospheric,background,bright,business,calm,clean,",
                date = LocalDate.parse("2026-06-20"),
                externalLink = URI.create("https://ccmixter.org/files/Puma_Tunes/70960"),
                license = CCLicense.CC_BY_NC,
                service = SearchService.CCMIXTER,
                popularity = null
            ),
            // No upload_extra: kept, without tempo or tags.
            SearchResult(
                author = "Reiswerk",
                title = "No extra metadata",
                duration = 148,
                bpm = 0,
                tags = "",
                date = LocalDate.parse("2014-07-01"),
                externalLink = URI.create("http://ccmixter.org/files/Reiswerk/90001"),
                license = CCLicense.CC_BY_NC,
                service = SearchService.CCMIXTER,
                popularity = 6
            )
            // The last upload's date cannot be read, and SearchResult.date is not nullable,
            // so that one alone is dropped.
        )

        assertEquals(expectedResults, results)
    }
}
