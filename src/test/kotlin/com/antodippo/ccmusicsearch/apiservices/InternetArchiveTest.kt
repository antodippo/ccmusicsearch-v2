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
class InternetArchiveTest {

    @Test
    fun testItFetchesAJsonAndReturnsAListOfSearchResults() = runBlocking {
        val internetArchive = InternetArchive(ApiClientTestDouble(SearchService.INTERNETARCHIVE), PrintDuration())
        val results = internetArchive.search("test")

        val expectedResults = listOf(
            SearchResult(
                author = "Natural Snow Buildings",
                title = "Aldebaran [2016]",
                duration = 0,
                bpm = 0,
                tags = "\"drone\", \"psychedelic\", \"folk\"",
                date = LocalDate.parse("2016-07-14"),
                externalLink = URI.create("https://archive.org/search?query=NaturalSnowBuildings-Aldebaran2016"),
                license = CCLicense.CC_BY_NC_ND,
                service = SearchService.INTERNETARCHIVE
            ),
            SearchResult(
                author = "El Chata de Vicalvaro",
                title = "Cante de Levante",
                duration = 0,
                bpm = 0,
                tags = "\"78rpm\", \"Folk\"",
                date = LocalDate.parse("2018-04-26"),
                externalLink = URI.create("https://archive.org/search?query=78_cante-de-levante-amores-no-ha-de-buscar"),
                license = CCLicense.UNKNOWN,
                service = SearchService.INTERNETARCHIVE
            )
        )

        assertEquals(expectedResults, results)
    }
}