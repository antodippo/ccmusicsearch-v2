package com.antodippo.ccmusicsearch

import com.antodippo.ccmusicsearch.apiservices.APIService
import com.antodippo.ccmusicsearch.apiservices.CCMixter
import com.antodippo.ccmusicsearch.apiservices.Jamendo
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatReadsFromFile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.time.LocalDate

@SpringBootTest
class SearchEngineTest {

    @Autowired
    private lateinit var springSearchEngine: SearchEngine

    // loc.gov answers every server-side client with a Cloudflare challenge, so the Library
    // is off unless switched on. This is the engine Spring builds, not one assembled by hand:
    // what it is given is every APIService bean, so the flag has to keep the bean out.
    @Test
    fun testTheLibraryOfCongressIsNotSearchedUnlessSwitchedOn() {
        assertEquals(
            listOf(
                SearchService.JAMENDO,
                SearchService.CCMIXTER,
                SearchService.INTERNETARCHIVE,
                SearchService.FREESOUND,
            ).sorted(),
            springSearchEngine.sources.sorted()
        )
    }

    @Test
    fun testSearchServicesAreCalledAndResultsAreMergedByRelevance() = runBlocking {
        val searchEngine = SearchEngine(
            listOf(
                Jamendo(ApiClientThatReadsFromFile("jamendo")),
                CCMixter(ApiClientThatReadsFromFile("ccmixter"))
            )
        )

        val results = searchEngine.search("test")

        // Services are interleaved by fused rank rather than concatenated, and no longer
        // ordered by date — the oldest of the four (2014-06-27) sits third.
        val expectedResults = listOf(
            SearchResult(
                author = "Arnold Wohler",
                title = "Old man corona-Blues",
                duration = 160,
                bpm = 0,
                tags = "",
                date = LocalDate.parse("2020-05-02"),
                externalLink = URI.create("https://www.jamendo.com/track/1760388"),
                license = CCLicense.CC_BY_SA,
                service = SearchService.JAMENDO
            ),
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
            ),
            SearchResult(
                author = "Agnes",
                title = "Znajdź swój blask",
                duration = 195,
                bpm = 0,
                tags = "",
                date = LocalDate.parse("2020-05-01"),
                externalLink = URI.create("https://www.jamendo.com/track/1759840"),
                license = CCLicense.CC_BY_NC_ND,
                service = SearchService.JAMENDO
            )
        )

        assertEquals(expectedResults, results)
    }

    @Test
    fun testOneServiceFailingDoesNotTakeDownTheRestOfTheSearch() = runBlocking {
        val searchEngine = SearchEngine(
            listOf(
                ServiceThatThrows(),
                Jamendo(ApiClientThatReadsFromFile("jamendo"))
            )
        )

        val results = searchEngine.search("test")

        assertEquals(2, results.size)
        assertTrue(results.all { it.service == SearchService.JAMENDO })
    }

    private class ServiceThatThrows : APIService {
        override val service = SearchService.INTERNETARCHIVE

        override suspend fun search(query: String): Collection<SearchResult> {
            throw NullPointerException("get(...) must not be null")
        }
    }

}

@SpringBootTest(properties = ["sources.libraryofcongress.enabled=true"])
class SearchEngineWithTheLibraryOfCongressSwitchedOnTest {

    @Autowired
    private lateinit var searchEngine: SearchEngine

    @Test
    fun testTheFlagBringsTheLibraryOfCongressBack() {
        assertTrue(SearchService.LIBRARYOFCONGRESS in searchEngine.sources)
        assertEquals(SearchService.values().size, searchEngine.sources.size)
    }
}
