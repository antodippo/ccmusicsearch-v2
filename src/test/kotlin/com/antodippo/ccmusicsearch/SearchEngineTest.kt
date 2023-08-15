package com.antodippo.ccmusicsearch

import com.antodippo.ccmusicsearch.apiservices.CCMixter
import com.antodippo.ccmusicsearch.apiservices.Jamendo
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatReadsFromFile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.time.LocalDate

@SpringBootTest
class SearchEngineTest {

    @Test
    fun testSearchServicesAreCalledAndResultsAreMerged() = runBlocking {
        val searchEngine = SearchEngine(
            listOf(
                Jamendo(ApiClientThatReadsFromFile("jamendo")),
                CCMixter(ApiClientThatReadsFromFile("ccmixter"))
            )
        )

        val results = searchEngine.search("test")

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
                author = "Agnes",
                title = "Znajdź swój blask",
                duration = 195,
                bpm = 0,
                tags = "",
                date = LocalDate.parse("2020-05-01"),
                externalLink = URI.create("https://www.jamendo.com/track/1759840"),
                license = CCLicense.CC_BY_NC_ND,
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
                service = SearchService.CCMIXTER
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
                service = SearchService.CCMIXTER
            )
        )

        assertEquals(expectedResults, results)
    }

}