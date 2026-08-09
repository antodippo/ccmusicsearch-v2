package com.antodippo.ccmusicsearch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.LocalDate

class SearchPageTest {

    private fun result(
        title: String = "a song",
        author: String = "an author",
        duration: Int = 120,
        bpm: Int = 0,
        tags: String = "",
        date: String = "2020-04-01",
        license: CCLicense = CCLicense.CC_BY,
        service: SearchService = SearchService.JAMENDO,
        popularity: Long? = null
    ) = SearchResult(
        author = author,
        title = title,
        duration = duration,
        bpm = bpm,
        tags = tags,
        date = LocalDate.parse(date),
        externalLink = URI.create("https://example.org/track"),
        license = license,
        service = service,
        popularity = popularity
    )

    private fun pageOf(vararg results: SearchResult) = SearchPage.from("jazz", results.toList())

    private fun songOf(result: SearchResult) = pageOf(result).results.first()

    @Test
    fun testItTellsAFirstVisitApartFromASearchThatFoundNothing() {
        val firstVisit = SearchPage.from(null, emptyList())
        assertFalse(firstVisit.hasQuery)
        assertFalse(firstVisit.hasResults)
        assertEquals("", firstVisit.q)

        val foundNothing = SearchPage.from("jazz", emptyList())
        assertTrue(foundNothing.hasQuery)
        assertFalse(foundNothing.hasResults)
    }

    @Test
    fun testItFormatsDurationsAsMinutesAndSeconds() {
        assertEquals("4:52", songOf(result(duration = 292)).durationLabel)
        assertEquals("0:07", songOf(result(duration = 7)).durationLabel)
        // Archival uploads run long enough to need the hour.
        assertEquals("1:02:03", songOf(result(duration = 3723)).durationLabel)
    }

    @Test
    fun testItShowsADashRatherThanAZeroForFiguresTheServiceNeverSent() {
        assertEquals("96", songOf(result(bpm = 96)).bpmLabel)
        assertEquals("—", songOf(result(bpm = 0)).bpmLabel)

        // The Internet Archive reports no length; "0:00" would read as a real one.
        assertEquals("—", songOf(result(duration = 0)).durationLabel)
    }

    @Test
    fun testItSplitsTagsWhicheverWayTheServiceJoinedThem() {
        // ccMixter joins on a bare comma, the others on a comma and a space.
        assertEquals(listOf("dance", "pop", "house"), songOf(result(tags = "dance,pop,house")).tagList)
        assertEquals(listOf("drone", "folk"), songOf(result(tags = "drone, folk")).tagList)

        val noTags = songOf(result(tags = ""))
        assertEquals(emptyList<String>(), noTags.tagList)
        assertFalse(noTags.hasTags)
    }

    @Test
    fun testItDropsRepeatedTagsAndKeepsTheRowFromOverflowing() {
        val song = songOf(result(tags = "jazz, jazz, piano, a, b, c, d, e, f, g"))

        assertEquals(listOf("jazz", "piano", "a", "b", "c", "d", "e"), song.tagList)
    }

    @Test
    fun testItNamesPopularityAfterWhateverTheServiceActuallyCounts() {
        assertEquals(
            "1,242 ratings",
            songOf(result(service = SearchService.CCMIXTER, popularity = 1242)).popularityLabel
        )
        assertEquals(
            "9,483 downloads",
            songOf(result(service = SearchService.INTERNETARCHIVE, popularity = 9483)).popularityLabel
        )

        // Jamendo exposes no popularity counter, so the row says so rather than showing a zero.
        val unranked = songOf(result(service = SearchService.JAMENDO, popularity = null))
        assertEquals("no popularity signal", unranked.popularityLabel)
        assertFalse(unranked.hasPopularity)
    }

    @Test
    fun testItOnlyPromisesCommercialUseWhereTheLicenceGrantsIt() {
        assertTrue(songOf(result(license = CCLicense.CC0)).commercialUseAllowed)
        assertTrue(songOf(result(license = CCLicense.PUBLIC_DOMAIN)).commercialUseAllowed)
        assertTrue(songOf(result(license = CCLicense.CC_BY_ND)).commercialUseAllowed)

        assertFalse(songOf(result(license = CCLicense.CC_BY_NC)).commercialUseAllowed)
        assertFalse(songOf(result(license = CCLicense.CC_BY_NC_SA)).commercialUseAllowed)
        // An unrecognised licence is not a grant, so it is not presented as one.
        assertFalse(songOf(result(license = CCLicense.UNKNOWN)).commercialUseAllowed)
    }

    @Test
    fun testItFormatsTheDateAsAMonthAndYear() {
        val song = songOf(result(date = "2021-04-12"))

        assertEquals("Apr 2021", song.dateLabel)
        assertEquals("2021-04-12", song.dateIso)
    }

    @Test
    fun testItKnowsWhenAnUploadHasNoCreatorToCredit() {
        assertTrue(songOf(result(author = "kellee")).hasAuthor)
        // Plenty of Internet Archive items have an empty creator field.
        assertFalse(songOf(result(author = "")).hasAuthor)
        assertFalse(songOf(result(author = "   ")).hasAuthor)
    }

    @Test
    fun testItKeepsEveryServiceInTheRailAndDisablesTheOnesThatDidNotAnswer() {
        val page = pageOf(
            result(service = SearchService.JAMENDO),
            result(service = SearchService.JAMENDO),
            result(service = SearchService.FREESOUND)
        )

        assertEquals(SearchService.values().size, page.facets.size)
        assertEquals(2, page.sourceCount)

        val jamendo = page.facets.first { it.key == "jamendo" }
        assertEquals("Jamendo", jamendo.label)
        assertEquals(2, jamendo.count)
        assertTrue(jamendo.enabled)

        val ccmixter = page.facets.first { it.key == "ccmixter" }
        assertEquals(0, ccmixter.count)
        assertFalse(ccmixter.enabled)
    }

    @Test
    fun testItCountsInWordsThatMatchTheNumber() {
        val one = pageOf(result())
        assertEquals(1, one.resultCount)
        assertEquals("result", one.resultNoun)
        assertEquals("source", one.sourceNoun)

        val several = pageOf(result(), result(service = SearchService.FREESOUND))
        assertEquals("results", several.resultNoun)
        assertEquals("sources", several.sourceNoun)

        assertEquals("results", SearchPage.from("jazz", emptyList()).resultNoun)
    }

    @Test
    fun testItOffersOnlyTheLicencesTheResultsActuallyUse() {
        val page = pageOf(
            result(license = CCLicense.CC_BY_NC),
            result(license = CCLicense.CC0),
            result(license = CCLicense.CC_BY_NC)
        )

        // Listed in licence order rather than in the order they turned up.
        assertEquals(listOf("CC0", "CC BY-NC"), page.licences.map { it.label })
    }

    @Test
    fun testItSizesTheLengthSliderToTheResultsItHas() {
        val page = pageOf(result(duration = 100), result(duration = 292))

        assertTrue(page.length.available)
        assertEquals(90, page.length.floor)
        assertEquals(300, page.length.ceiling)
        assertEquals("1:30 – 5:00", page.length.valueLabel)
    }

    @Test
    fun testItHidesTheTempoSliderWhenNothingReportsABpm() {
        val noTempo = pageOf(result(bpm = 0), result(bpm = 0))
        assertFalse(noTempo.tempo.available)

        val withTempo = pageOf(result(bpm = 96), result(bpm = 112))
        assertTrue(withTempo.tempo.available)
        assertEquals(90, withTempo.tempo.floor)
        assertEquals(120, withTempo.tempo.ceiling)
    }

    @Test
    fun testItAlwaysLeavesTheSlidersSomethingToSlideAlong() {
        // One result, or several of the same length, must not collapse into a zero-width range.
        val page = pageOf(result(duration = 120, bpm = 100))

        assertTrue(page.length.ceiling > page.length.floor)
        assertTrue(page.tempo.ceiling > page.tempo.floor)
    }

    @Test
    fun testItCyclesTheDiscColoursSoALongListStillReads() {
        val page = SearchPage.from("jazz", (1..8).map { result(title = "song $it") })

        assertEquals("disc-1", page.results[0].discClass)
        assertEquals("disc-6", page.results[5].discClass)
        assertEquals("disc-1", page.results[6].discClass)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), page.results.map { it.position })
    }
}
