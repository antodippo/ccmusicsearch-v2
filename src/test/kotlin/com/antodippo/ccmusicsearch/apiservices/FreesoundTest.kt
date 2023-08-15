package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatReadsFromFile
import com.antodippo.ccmusicsearch.CCLicense
import com.antodippo.ccmusicsearch.SearchResult
import com.antodippo.ccmusicsearch.SearchService
import com.antodippo.ccmusicsearch.infra.PrintDuration
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatThrows
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.time.LocalDate

@SpringBootTest
class FreesoundTest {

    @Test
    fun testItFetchesAJsonAndReturnsAListOfSearchResults() = runBlocking {
        val freesound = Freesound(ApiClientThatReadsFromFile("freesound"), PrintDuration())
        val results = freesound.search("test")

        val expectedResults = listOf(
            SearchResult(
                author = "daviddryden",
                title = "1025 Desending guitar shuffle rag bluegrass finger picking Gsus2 add 11_F#m_E11_D.WAV",
                duration = 29,
                bpm = 0,
                tags = "\"descending\", \"picked\", \"walking\", \"airy\", \"lively\", \"shuffle\", \"rag\"",
                date = LocalDate.parse("2023-08-05"),
                externalLink = URI.create("https://freesound.org/people/daviddryden/sounds/698458/"),
                license = CCLicense.CC_BY_NC,
                service = SearchService.FREESOUND
            ),
            SearchResult(
                author = "daviddryden",
                title = "1021 E11 chord arpeggio guitar.WAV",
                duration = 19,
                bpm = 0,
                tags = "\"descending\", \"acoustic\", \"picked\", \"mystical\", \"E11\", \"airy\"",
                date = LocalDate.parse("2023-08-05"),
                externalLink = URI.create("https://freesound.org/people/daviddryden/sounds/698456/"),
                license = CCLicense.CC_BY_NC,
                service = SearchService.FREESOUND
            )
        )

        assertEquals(expectedResults, results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientThrowsAnException() = runBlocking {
        val freesound = Freesound(ApiClientThatThrows(), PrintDuration())
        val results = freesound.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyJson() = runBlocking {
        val freesound = Freesound(ApiClientThatReadsFromFile("emptyresponse"), PrintDuration())
        val results = freesound.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

}