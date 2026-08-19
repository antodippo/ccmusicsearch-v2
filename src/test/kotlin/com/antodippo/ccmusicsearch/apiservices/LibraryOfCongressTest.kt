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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@SpringBootTest
class LibraryOfCongressTest {

    /**
     * Pinned so the public domain cut-off does not move under the assertions. On this date
     * the Music Modernization Act has released everything published up to and including
     * 1925.
     */
    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC)

    /** The first three results of a real `q=jazz` response, fields untouched. */
    @Test
    fun testItFetchesAJsonAndReturnsAListOfSearchResults() = runBlocking {
        val libraryOfCongress = LibraryOfCongress(ApiClientThatReadsFromFile("libraryofcongress"), clock)
        val results = libraryOfCongress.search("test")

        val expectedResults = listOf(
            SearchResult(
                author = "Benson Orchestra Of Chicago",
                title = "Oklahoma Indian jazz",
                duration = 0,
                bpm = 0,
                // "ragtime, jazz, and more" is one heading, kept whole; the fourth heading
                // does not fit inside the budget and is dropped rather than cut short.
                tags = "ragtime / jazz / and more, instrumental, victor",
                date = LocalDate.parse("1923-11-07"),
                externalLink = URI.create("https://www.loc.gov/item/jukebox-68453/"),
                license = CCLicense.PUBLIC_DOMAIN,
                service = SearchService.LIBRARYOFCONGRESS
            ),
            SearchResult(
                // contributor lists Rosario Bourdon, the conductor, first. The singer is
                // who the record is by, and contributor_primary is the field that says so.
                author = "Harris, Marion",
                title = "Jazz baby",
                duration = 0,
                bpm = 0,
                tags = "victor, humorous songs, ragtime / jazz / and more, vocal",
                date = LocalDate.parse("1919-04-18"),
                externalLink = URI.create("https://www.loc.gov/item/jukebox-31920/"),
                license = CCLicense.PUBLIC_DOMAIN,
                service = SearchService.LIBRARYOFCONGRESS
            ),
            SearchResult(
                // Likewise: contributor opens with Tom Delaney, who wrote it.
                author = "Original Dixieland Jazz Band",
                title = "Jazz me blues",
                duration = 0,
                bpm = 0,
                tags = "blues, ragtime / jazz / and more, instrumental, victor",
                date = LocalDate.parse("1921-05-03"),
                externalLink = URI.create("https://www.loc.gov/item/jukebox-40212/"),
                license = CCLicense.PUBLIC_DOMAIN,
                service = SearchService.LIBRARYOFCONGRESS
            )
        )

        assertEquals(expectedResults, results)
    }

    @Test
    fun testItCopesWithResultsThatAreNotUsableRecordings() = runBlocking {
        val libraryOfCongress =
            LibraryOfCongress(ApiClientThatReadsFromFile("libraryofcongresswithoddmetadata"), clock)
        val results = libraryOfCongress.search("test")

        val expectedResults = listOf(
            SearchResult(
                // Already capitalised, so the interior capital survives untouched.
                author = "McDonald, Fred",
                title = "Bare string subject, no url, year only",
                duration = 0,
                bpm = 0,
                // subject as a bare string rather than a list.
                tags = "vocal",
                // A bare catalogue year rather than a full date.
                date = LocalDate.parse("1912-01-01"),
                // No url, so the id stands in.
                externalLink = URI.create("http://www.loc.gov/item/jukebox-00001/"),
                license = CCLicense.PUBLIC_DOMAIN,
                service = SearchService.LIBRARYOFCONGRESS
            ),
            SearchResult(
                // No contributor_primary, so it falls back to contributor.
                author = "Someone, Else",
                title = "No online_format at all",
                duration = 0,
                bpm = 0,
                tags = "",
                // No date, so the timestamp in dates is used.
                date = LocalDate.parse("1915-06-01"),
                externalLink = URI.create("https://www.loc.gov/item/jukebox-00002/"),
                license = CCLicense.PUBLIC_DOMAIN,
                service = SearchService.LIBRARYOFCONGRESS
            ),
            SearchResult(
                author = "Someone, Recent",
                title = "Too recent to be public domain",
                duration = 0,
                bpm = 0,
                tags = "vocal",
                date = LocalDate.parse("1960-04-02"),
                externalLink = URI.create("https://www.loc.gov/item/jukebox-00003/"),
                // Past the Music Modernization Act line, so we decline to call it public
                // domain rather than clearing it for commercial reuse.
                license = CCLicense.UNKNOWN,
                service = SearchService.LIBRARYOFCONGRESS
            )
        )

        // The collection landing page is dropped for not being audio, and the undated
        // cylinder is dropped rather than given an invented date. The item with no
        // online_format at all is kept: the query is already pinned to an audio
        // collection, so a missing field is not evidence against it.
        assertEquals(expectedResults, results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientThrowsAnException() = runBlocking {
        val libraryOfCongress = LibraryOfCongress(ApiClientThatThrows(), clock)
        val results = libraryOfCongress.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyJson() = runBlocking {
        val libraryOfCongress = LibraryOfCongress(ApiClientThatReadsFromFile("emptyresponse"), clock)
        val results = libraryOfCongress.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItReturnsAnEmptyListWhenTheClientReturnsAnEmptyBody() = runBlocking {
        val libraryOfCongress = LibraryOfCongress(ApiClientThatReturnsAnEmptyBody(), clock)
        val results = libraryOfCongress.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }
}
