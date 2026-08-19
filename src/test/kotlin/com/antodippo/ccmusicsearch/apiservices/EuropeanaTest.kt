package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.CCLicense
import com.antodippo.ccmusicsearch.SearchResult
import com.antodippo.ccmusicsearch.SearchService
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatReadsFromFile
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatReturnsAnEmptyBody
import com.antodippo.ccmusicsearch.testdoubles.ApiClientThatThrows
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.time.LocalDate

@SpringBootTest
class EuropeanaTest {

    @Test
    fun testItFetchesAJsonAndReturnsAListOfSearchResults() = runBlocking {
        val europeana = Europeana(ApiClientThatReadsFromFile("europeana"))
        val results = europeana.search("test")

        val expectedResults = listOf(
            SearchResult(
                author = "George Gershwin",
                title = "Rhapsody in Blue",
                duration = 0,
                bpm = 0,
                tags = "jazz, orchestral, piano",
                // The recording year, not the day Europeana indexed it.
                date = LocalDate.parse("1924-01-01"),
                externalLink = URI.create("https://www.europeana.eu/item/2059209/data_sounds_R_10_1234"),
                license = CCLicense.CC_BY_SA,
                service = SearchService.EUROPEANA
            ),
            SearchResult(
                // No dcCreator, so the holding institution stands in for the performer.
                author = "Suomen kansallisarkisto",
                title = "Folk song from Lapland",
                duration = 0,
                bpm = 0,
                // dcSubject as a bare string rather than a list.
                tags = "field recording",
                // No year either, so this falls back to when Europeana ingested it.
                date = LocalDate.parse("2018-11-20"),
                externalLink = URI.create("https://www.europeana.eu/item/9200518/field_recording_88"),
                license = CCLicense.CC0,
                service = SearchService.EUROPEANA
            )
        )

        // The third item in the fixture carries no date of any kind and is dropped rather
        // than given an invented one.
        assertEquals(expectedResults, results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientThrowsAnException() = runBlocking {
        val europeana = Europeana(ApiClientThatThrows())
        val results = europeana.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyJson() = runBlocking {
        val europeana = Europeana(ApiClientThatReadsFromFile("emptyresponse"))
        val results = europeana.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyBody() = runBlocking {
        val europeana = Europeana(ApiClientThatReturnsAnEmptyBody())
        val results = europeana.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }
}
