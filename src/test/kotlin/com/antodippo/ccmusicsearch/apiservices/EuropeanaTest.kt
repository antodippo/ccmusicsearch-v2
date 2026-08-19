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

    /**
     * The first three results of a real `q=jazz` response. Values are untouched; the
     * multilingual label blocks are trimmed, and `link` and `guid` are dropped entirely
     * because Europeana echoes the caller's wskey back inside them.
     */
    @Test
    fun testItFetchesAJsonAndReturnsAListOfSearchResults() = runBlocking {
        val europeana = Europeana(ApiClientThatReadsFromFile("europeana"))
        val results = europeana.search("test")

        val expectedResults = listOf(
            SearchResult(
                // No dcCreator on any of these, so the broadcaster that holds the tape
                // stands in for a performer the metadata never names.
                author = "Romanian Radio Broadcasting Company",
                title = "Jazz etude \"Pastorale\"",
                duration = 0,
                bpm = 0,
                tags = "Archaeus Ensemble, 20th century, chamber music, The twentieth century",
                // The concert, not the day Europeana catalogued it — that was 2023.
                date = LocalDate.parse("2011-03-08"),
                externalLink = URI.create(
                    "https://www.europeana.eu/item/937/Culturalia_9e8523a4_e91b_49b9_b44d_953475239cd4"
                ),
                license = CCLicense.CC_BY_SA,
                service = SearchService.EUROPEANA
            ),
            SearchResult(
                author = "Romanian Radio Broadcasting Company",
                title = "Temă de jazz",
                duration = 0,
                bpm = 0,
                tags = "Radio Recording, Mogov Palace, chamber music, Ion Bogdan Stefannescu",
                date = LocalDate.parse("2010-02-27"),
                externalLink = URI.create(
                    "https://www.europeana.eu/item/937/Culturalia_3707cbcb_5163_4c66_b830_522649b559b8"
                ),
                license = CCLicense.CC_BY_SA,
                service = SearchService.EUROPEANA
            ),
            SearchResult(
                author = "Romanian Radio Broadcasting Company",
                title = "Pass me the Jazz",
                duration = 0,
                bpm = 0,
                tags = "20th century, The twentieth century",
                date = LocalDate.parse("2015-10-02"),
                externalLink = URI.create(
                    "https://www.europeana.eu/item/937/Culturalia_a71f9985_41cf_4d86_84fd_393a76fd237a"
                ),
                license = CCLicense.CC_BY_SA,
                service = SearchService.EUROPEANA
            )
        )

        assertEquals(expectedResults, results)
    }

    @Test
    fun testItCopesWithTheFieldsProvidersLeaveOut() = runBlocking {
        val europeana = Europeana(ApiClientThatReadsFromFile("europeanawithoddmetadata"))
        val results = europeana.search("test")

        val expectedResults = listOf(
            SearchResult(
                // dcCreator is present here, and wins over dataProvider.
                author = "Anabela Marica",
                title = "Documented field names",
                duration = 0,
                bpm = 0,
                // Plain dcSubject, which the documentation describes and few providers send.
                tags = "piano, recital",
                // Plain `year`, likewise documented and rare. Only a year, so January.
                date = LocalDate.parse("1932-01-01"),
                externalLink = URI.create("https://www.europeana.eu/item/111/documented"),
                // edmRights rather than rights, and CC0 must not be read as public domain:
                // fromUrl tests /zero/ before /publicdomain/, and both are in the URL.
                license = CCLicense.CC0,
                service = SearchService.EUROPEANA
            ),
            SearchResult(
                author = "",
                title = "No subjects in English",
                duration = 0,
                bpm = 0,
                // Only "def" and "ro" are offered. def holds Getty URIs, so Romanian wins.
                tags = "muzică de cameră, pian",
                // No zxx label, so the literal entry in edmTimespan is used and the century
                // URIs sitting beside it are passed over.
                date = LocalDate.parse("1998-07-14"),
                externalLink = URI.create("https://www.europeana.eu/item/222/nonenglish"),
                license = CCLicense.UNKNOWN,
                service = SearchService.EUROPEANA
            ),
            SearchResult(
                author = "A Provider",
                title = "Nothing but an ingest date",
                duration = 0,
                bpm = 0,
                tags = "",
                // Last resort: when the record carries no recording date at all, the day
                // Europeana ingested it beats dropping the row.
                date = LocalDate.parse("2021-09-30"),
                externalLink = URI.create("https://www.europeana.eu/item/333/ingestonly"),
                license = CCLicense.CC_BY,
                service = SearchService.EUROPEANA
            )
        )

        // The item with no id at all is dropped: there would be nothing to link to.
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
