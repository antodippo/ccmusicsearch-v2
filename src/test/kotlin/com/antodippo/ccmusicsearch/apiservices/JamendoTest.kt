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
class JamendoTest {

    @Test
    fun testItFetchesAJsonAndReturnsAListOfSearchResults() = runBlocking {
        val jamendo = Jamendo(ApiClientTestDouble(SearchService.JAMENDO), PrintDuration())
        val results = jamendo.search("test")

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
            )
        )

        assertEquals(expectedResults, results)
    }
}