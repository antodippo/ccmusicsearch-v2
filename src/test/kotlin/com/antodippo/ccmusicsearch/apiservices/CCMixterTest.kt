package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.ApiClientTestDouble
import com.antodippo.ccmusicsearch.CCLicense
import com.antodippo.ccmusicsearch.SearchResult
import com.antodippo.ccmusicsearch.SearchService
import com.antodippo.ccmusicsearch.infra.PrintDuration
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
        val ccMixter = CCMixter(ApiClientTestDouble(SearchService.CCMIXTER), PrintDuration())
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