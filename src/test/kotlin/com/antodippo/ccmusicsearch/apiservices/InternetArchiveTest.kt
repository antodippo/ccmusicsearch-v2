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
import org.junit.jupiter.api.function.Executable
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.net.URLDecoder
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
                tags = "drone, psychedelic, folk",
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
                tags = "78rpm, Folk",
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
                tags = "drone, psychedelic, folk",
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

    // The archive answers a query it cannot parse with 200 and {"error": "…"}, no "response"
    // key at all. Reading through to docs NPE'd on that, twenty times in a fortnight.
    @Test
    fun testItReturnsAnEmptyListWhenTheArchiveRejectsTheQuery() = runBlocking {
        val internetArchive = InternetArchive(ApiClientThatReadsFromFile("internetarchiveerror"))
        val results = internetArchive.search("test")

        assertEquals(emptyList<SearchResult>(), results)
    }

    @Test
    fun testItSendsTheCleanedQueryInsideTheMusicOnlyFilter() = runBlocking {
        val apiClient = ApiClientThatRecords("internetarchive")
        InternetArchive(apiClient).search("ac/dc")

        val sent = URLDecoder.decode(apiClient.requests.single().rawQuery, "UTF-8")
        assertTrue(sent.startsWith("q=(ac dc) AND mediatype:(audio) AND "), sent)
    }

    @Test
    fun testItDoesNotAskTheArchiveWhenNothingSearchableIsLeft() = runBlocking {
        val apiClient = ApiClientThatRecords("internetarchive")
        val results = InternetArchive(apiClient).search("\"*\"")

        assertEquals(emptyList<SearchResult>(), results)
        assertTrue(apiClient.requests.isEmpty())
    }

    // The first group were typed into the live site and each cost that search its Internet
    // Archive results. Every expected value on the right was checked against the archive,
    // which parses it.
    @Test
    fun testItDropsTheSyntaxTheArchiveCannotParse() {
        assertArchiveQueries(
            "jazz" to "(jazz)",
            "ac/dc" to "(ac dc)",
            "csma/cd" to "(csma cd)",
            "\"jazz" to "(jazz)",
            "camila}" to "(camila)",
            "umate.me/ddlalalo" to "(umate me ddlalalo)",
            "24\" TO MM" to "(24 to MM)",
            "METAL GEAR SOLID - Master Collection [UPD3.0.0]" to "(METAL GEAR SOLID Master Collection UPD3 0 0)",
            "1992/5/9\u3000横浜大洋対巨人" to "(1992 5 9 横浜大洋対巨人)",
            "jazz OR" to "(jazz)",
            "c++" to "(c)",
            "a:b" to "(a b)",
            "jazz_blues" to "(jazz blues)",
            // A hyphen or an apostrophe survives only inside a word.
            "hip-hop" to "(hip-hop)",
            "jazz -" to "(jazz)",
            "jazz --live" to "(jazz live)",
            "don't stop" to "(don't stop)",
            "don’t stop" to "(don't stop)",
            "o'connor" to "(o'connor)",
            "rock 'n' roll" to "(rock n roll)",
        )
    }

    // Exclusions, operators and phrases already parsed, and have to keep meaning what they
    // meant. Flattening "jazz -live" to its words would search for jazz *and* live — the
    // opposite set — and a lower-cased OR is a word the archive then insists on: "jazz or
    // blues" matches 197 items, "jazz OR blues" over forty thousand. Brackets are the one
    // thing given up: they are dropped rather than balanced, so explicit grouping is lost.
    @Test
    fun testItKeepsTheSyntaxThatAlreadyWorked() {
        assertArchiveQueries(
            "jazz -live" to "(jazz -live)",
            "-jazz" to "(-jazz)",
            "jazz NOT live" to "(jazz NOT live)",
            "NOT jazz AND blues" to "(NOT jazz AND blues)",
            "jazz OR blues" to "(jazz OR blues)",
            "a OR b OR c" to "(a OR b OR c)",
            "\"love song\"" to "(\"love song\")",
            "\"love song\" jazz" to "(\"love song\" jazz)",
            "NOT \"love song\" jazz" to "(NOT \"love song\" jazz)",
        )
    }

    // An operator needs a term on the side it applies to; one that has none is dropped, and
    // an operator name that is part of other text is only a word.
    @Test
    fun testItDropsOperatorsWithNothingToApplyTo() {
        assertArchiveQueries(
            "OR jazz" to "(jazz)",
            "jazz NOT" to "(jazz)",
            "jazz AND OR blues" to "(jazz OR blues)",
            "jazz AND NOT live" to "(jazz NOT live)",
            "jazz OR NOT" to "(jazz)",
            "NOT NOT jazz" to "(NOT jazz)",
            "NOT -live" to "(-live)",
            "jazz AND, blues" to "(jazz and blues)",
            "\"rock AND roll\"" to "(\"rock and roll\")",
        )
    }

    // Letters beyond ASCII, combining marks included, are words like any other.
    @Test
    fun testItKeepsWordsInEveryScript() {
        assertArchiveQueries(
            "日本" to "(日本)",
            "-日本" to "(-日本)",
            "งานเลี้ยงเพชร" to "(งานเลี้ยงเพชร)",
            "सुकन्या समृद्धि" to "(सुकन्या समृद्धि)",
            "Алена Росс — Белый теплоход" to "(Алена Росс Белый теплоход)",
            "cafe\u0301 del mar" to "(caf\u00e9 del mar)",
        )
    }

    @Test
    fun testItFindsNoWordsInPunctuationAlone() {
        listOf("\"", "\"\"", "*", "-", "_", "   ", "' - '", "()", "AND", "OR NOT").forEach {
            assertNull(archiveQuery(it), "for $it")
        }
    }

    private fun assertArchiveQueries(vararg expectations: Pair<String, String>) =
        assertAll(expectations.map { (typed, expected) ->
            Executable { assertEquals(expected, archiveQuery(typed), "for $typed") }
        })
}
