package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatReadsFromFile
import com.antodippo.ccmusicsearch.CCLicense
import com.antodippo.ccmusicsearch.SearchResult
import com.antodippo.ccmusicsearch.SearchService
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatThrows
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.time.LocalDate

@SpringBootTest
class Icons8Test {

    @Test
    fun testItFetchesAJsonAndReturnsAListOfSearchResults() = runBlocking {
        val icons8 = Icons8(ApiClientThatReadsFromFile("icons8"))
        val results = icons8.search("test")

        val expectedResults = listOf(
            SearchResult(
                author = "Sunny Music",
                title = "Creepy Orchestra",
                duration = 76,
                bpm = 128,
                tags = "\"Atmospheric\", \"Beautiful\", \"Easy Listening\"",
                date = LocalDate.parse("2023-08-02"),
                externalLink = URI.create("https://music-cdn.icons8.com/preview_low/100/2e7cedd0-2ba1-4f7a-8467-077cff6c144a.mp3"),
                license = CCLicense.UNKNOWN,
                service = SearchService.ICONS8
            ),
            SearchResult(
                author = "Sunny Music",
                title = "Chill Intro",
                duration = 23,
                bpm = 124,
                tags = "",
                date = LocalDate.parse("2023-08-02"),
                externalLink = URI.create("https://music-cdn.icons8.com/preview_low/839/e4e53f38-7320-45e3-8019-28e03707d3bf.mp3"),
                license = CCLicense.UNKNOWN,
                service = SearchService.ICONS8
            )
        )

        assertEquals(expectedResults, results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientThrowsAnException() = runBlocking {
        val icons8 = Icons8(ApiClientThatThrows())
        val results = icons8.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyJson() = runBlocking {
        val icons8 = Icons8(ApiClientThatReadsFromFile("emptyresponse"))
        val results = icons8.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }
}