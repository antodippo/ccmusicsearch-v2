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
class LibraryOfCongressTest {

    @Test
    fun testItFetchesAJsonAndReturnsAListOfSearchResults() = runBlocking {
        val libraryOfCongress = LibraryOfCongress(ApiClientThatReadsFromFile("libraryofcongress"))
        val results = libraryOfCongress.search("test")

        val expectedResults = listOf(
            SearchResult(
                // Catalogued lower-case; only the leading letters are touched.
                author = "Sousa, John Philip",
                title = "The Stars and Stripes Forever",
                duration = 0,
                bpm = 0,
                tags = "marches, band music, patriotic music",
                // The bare year the recording is catalogued under.
                date = LocalDate.parse("1909-01-01"),
                externalLink = URI.create("https://www.loc.gov/item/jukebox-12345/"),
                license = CCLicense.PUBLIC_DOMAIN,
                service = SearchService.LIBRARYOFCONGRESS
            ),
            SearchResult(
                // Already capitalised, and left exactly as catalogued.
                author = "Original Dixieland Jass Band",
                title = "Livery Stable Blues",
                duration = 0,
                bpm = 0,
                // subject as a bare string rather than a list.
                tags = "jazz",
                // No `date`, so the full timestamp in `dates` is used.
                date = LocalDate.parse("1917-02-26"),
                externalLink = URI.create("https://www.loc.gov/item/jukebox-67890/"),
                license = CCLicense.PUBLIC_DOMAIN,
                service = SearchService.LIBRARYOFCONGRESS
            )
        )

        // The fixture also holds a collection landing page, dropped for not being audio, and
        // an item with no date at all, dropped rather than given an invented one.
        assertEquals(expectedResults, results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientThrowsAnException() = runBlocking {
        val libraryOfCongress = LibraryOfCongress(ApiClientThatThrows())
        val results = libraryOfCongress.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyJson() = runBlocking {
        val libraryOfCongress = LibraryOfCongress(ApiClientThatReadsFromFile("emptyresponse"))
        val results = libraryOfCongress.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyBody() = runBlocking {
        val libraryOfCongress = LibraryOfCongress(ApiClientThatReturnsAnEmptyBody())
        val results = libraryOfCongress.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }
}
