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
class InternetArchiveTest {

    @Test
    fun testItFetchesAJsonAndReturnsAListOfSearchResults() = runBlocking {
        val internetArchive = InternetArchive(ApiClientThatReadsFromFile("internetarchive"))
        val results = internetArchive.search("test")

        val expectedResults = listOf(
            SearchResult(
                author = "Natural Snow Buildings",
                title = "Aldebaran [2016]",
                duration = 0,
                bpm = 0,
                tags = "\"drone\", \"psychedelic\", \"folk\"",
                date = LocalDate.parse("2016-07-14"),
                externalLink = URI.create("https://archive.org/details/NaturalSnowBuildings-Aldebaran2016"),
                license = CCLicense.CC_BY_NC_ND,
                service = SearchService.INTERNETARCHIVE,
                popularity = 2300
            ),
            SearchResult(
                author = "El Chata de Vicalvaro",
                title = "Cante de Levante",
                duration = 0,
                bpm = 0,
                tags = "\"78rpm\", \"Folk\"",
                date = LocalDate.parse("2018-04-26"),
                externalLink = URI.create("https://archive.org/details/78_cante-de-levante-amores-no-ha-de-buscar"),
                license = CCLicense.UNKNOWN,
                service = SearchService.INTERNETARCHIVE,
                popularity = 500
            )
        )

        assertEquals(expectedResults, results)
    }

    // The archive's metadata is supplied by uploaders, so fields the schema lists are
    // routinely absent: a live query for "jazz" comes back with one item in fifty that
    // carries no subject at all, and a handful whose subject is a bare string rather
    // than a list. One of those cost us every search in production, because the mapping
    // ran outside the try/catch and SearchEngine's coroutineScope propagated the throw
    // to the other services.
    @Test
    fun testItSkipsItemsWithUnusableMetadataRatherThanFailingTheWholeSearch() = runBlocking {
        val internetArchive = InternetArchive(ApiClientThatReadsFromFile("internetarchivewithoddmetadata"))
        val results = internetArchive.search("test")

        val expectedResults = listOf(
            SearchResult(
                author = "Natural Snow Buildings",
                title = "Aldebaran [2016]",
                duration = 0,
                bpm = 0,
                tags = "\"drone\", \"psychedelic\", \"folk\"",
                date = LocalDate.parse("2016-07-14"),
                externalLink = URI.create("https://archive.org/details/NaturalSnowBuildings-Aldebaran2016"),
                license = CCLicense.CC_BY_NC_ND,
                service = SearchService.INTERNETARCHIVE,
                popularity = 2300
            ),
            // No subject: kept, with empty tags.
            SearchResult(
                author = "Aleksi Eeben",
                title = "Aleksi Eeben - The Four Tales [mtk201]",
                duration = 0,
                bpm = 0,
                tags = "",
                date = LocalDate.parse("2008-04-26"),
                externalLink = URI.create("https://archive.org/details/mtk201"),
                license = CCLicense.CC_BY_NC_ND,
                service = SearchService.INTERNETARCHIVE,
                popularity = 6690
            ),
            // A bare string rather than a list of subjects: also kept, and the string is
            // used as the tags rather than being dropped on the floor.
            SearchResult(
                author = "Left",
                title = "Another Hour of EMCradio",
                duration = 0,
                bpm = 0,
                tags = "EMCradio, compilation, independent, eclectic",
                date = LocalDate.parse("2006-07-23"),
                externalLink = URI.create("https://archive.org/details/EMC23002Another_hour_of_EMCradio"),
                license = CCLicense.CC_BY_NC_ND,
                service = SearchService.INTERNETARCHIVE,
                popularity = 18251
            )
            // The last two docs in the fixture have no publicdate and no identifier
            // respectively. Neither has a sensible stand-in, so they are dropped.
        )

        assertEquals(expectedResults, results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientThrowsAnException() = runBlocking {
        val internetArchive = InternetArchive(ApiClientThatThrows())
        val results = internetArchive.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyJson() = runBlocking {
        val internetArchive = InternetArchive(ApiClientThatReadsFromFile("emptyresponse"))
        val results = internetArchive.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyBody() = runBlocking {
        val internetArchive = InternetArchive(ApiClientThatReturnsAnEmptyBody())
        val results = internetArchive.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }
}