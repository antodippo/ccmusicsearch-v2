package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatReadsFromFile
import com.antodippo.ccmusicsearch.CCLicense
import com.antodippo.ccmusicsearch.SearchResult
import com.antodippo.ccmusicsearch.SearchService
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatRecords
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatReturnsAnEmptyBody
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatThrows
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
        val jamendo = Jamendo(ApiClientThatReadsFromFile("jamendo"))
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

    @Test
    fun testItReturnsAnEmptyListWhenTheClientThrowsAnException() = runBlocking {
        val jamendo = Jamendo(ApiClientThatThrows())
        val results = jamendo.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyJson() = runBlocking {
        val jamendo = Jamendo(ApiClientThatReadsFromFile("emptyresponse"))
        val results = jamendo.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyBody() = runBlocking {
        val jamendo = Jamendo(ApiClientThatReturnsAnEmptyBody())
        val results = jamendo.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    // Jamendo answers a good share of ordinary searches with status "success" and an empty
    // page — six of thirty identical "jazz" requests — and the next request is almost always
    // full. That was the 0, 200, 0 seen when refreshing a search.
    @Test
    fun testItAsksAgainWhenJamendoAnswersWithAnEmptyPage() = runBlocking {
        val apiClient = ApiClientThatRecords("jamendoemptypage", "jamendo")
        val results = Jamendo(apiClient).search("jazz")

        assertEquals(2, results.size)
        assertEquals(2, apiClient.requests.size)
        assertEquals(apiClient.requests[0], apiClient.requests[1])
    }

    // An empty page is also what a search that matches nothing looks like, so the asking
    // again has to stop somewhere.
    @Test
    fun testItStopsAskingAfterThreeEmptyPages() = runBlocking {
        val apiClient = ApiClientThatRecords("jamendoemptypage")
        val results = Jamendo(apiClient).search("zqxjvwkpfhgqzz")

        assertEquals(emptyList<SearchResult>(), results)
        assertEquals(3, apiClient.requests.size)
    }

    // A refusal — here the real answer to a bad key — is not going to change on the next
    // request, so it is logged rather than retried.
    @Test
    fun testItDoesNotAskAgainWhenJamendoRefusesTheSearch() = runBlocking {
        val apiClient = ApiClientThatRecords("jamendofailed", "jamendo")
        val results = Jamendo(apiClient).search("jazz")

        assertEquals(emptyList<SearchResult>(), results)
        assertEquals(1, apiClient.requests.size)
    }

    @Test
    fun testItSkipsUnreadableTracksRatherThanTheWholePage() = runBlocking {
        val jamendo = Jamendo(ApiClientThatReadsFromFile("jamendowithoddmetadata"))
        val results = jamendo.search("test")

        // "0000-00-00" as a release date and a missing share URL cost those two tracks
        // only; the fixture's two real tracks come through as usual.
        assertEquals(listOf("1760388", "1759840"), results.map { it.externalLink.path.substringAfterLast('/') })
    }
}
