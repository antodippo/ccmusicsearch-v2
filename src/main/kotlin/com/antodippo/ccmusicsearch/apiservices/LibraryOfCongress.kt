package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpResponse
import java.time.Clock
import java.time.LocalDate

@Service
class LibraryOfCongress(
    private val apiClient: APIClient,
    // Injected so the public domain cut-off below can be pinned in tests. It moves every
    // January, and a test that agreed with it only until next new year would be worse than
    // no test at all.
    private val clock: Clock = Clock.systemUTC(),
) : APIService {

    private val logger = KotlinLogging.logger {}

    override suspend fun search(query: String): List<SearchResult> {
        val escapedQuery = URLEncoder.encode(query, "UTF-8")

        val response: HttpResponse<String>
        val items: JsonNode?
        try {
            // Scoped to the National Jukebox rather than to loc.gov/audio at large. The wider
            // audio endpoint mixes in material of every vintage and states its rights as free
            // prose rather than as a licence URL, so there would be nothing to read a licence
            // from; the Jukebox is a single dated collection, which is what lets license()
            // below decide per record.
            //
            // at=results trims the response to the array we read. No API key: the Library
            // rate-limits instead of authenticating.
            response = apiClient.get(
                URI(
                    "https://www.loc.gov/audio/?q=$escapedQuery&fa=$JUKEBOX_FACET" +
                        "&fo=json&at=results&c=$COUNT"
                )
            )
            val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
            items = jsonBody["results"]
        } catch (e: Exception) {
            logger.error { "Error while searching on the Library of Congress: ${e.message}" }
            return emptyList()
        }

        if (items != null && items.isArray) {
            return items.filter { isAudio(it) }.mapNotNull { toSearchResult(it) }
        }

        return emptyList()
    }

    /**
     * Null when the item cannot be turned into a result at all.
     *
     * Catalogue records here are far more uniform than Europeana's or the Internet Archive's,
     * but a search result can still be a collection landing page rather than a recording, so
     * nothing is taken on trust. Anything unusable costs us that one item — never the
     * response, and never the other services.
     */
    private fun toSearchResult(item: JsonNode): SearchResult? {
        val link = firstOf(item, "url") ?: firstOf(item, "id") ?: return null
        val date = recordedOn(item) ?: return null

        return try {
            SearchResult(
                // contributor_primary, not contributor: the latter lists everyone involved
                // in cataloguing order, which puts the composer or the conductor first as
                // often as the performer. On a real page of results it credited "Jazz baby"
                // to its conductor rather than to Marion Harris, who sang it.
                author = performer(item) ?: "",
                title = firstOf(item, "title") ?: "",
                // Not in the search response; it lives on the item record, which would be a
                // second request each. The length filter already skips results reporting
                // zero, as it does for Internet Archive and Europeana.
                duration = 0,
                bpm = 0,
                tags = tags(item["subject"]),
                date = date,
                externalLink = URI.create(link),
                license = license(date),
                service = SearchService.LIBRARYOFCONGRESS
            )
        } catch (e: Exception) {
            logger.warn { "Skipping Library of Congress item $link: ${e.message}" }
            null
        }
    }

    /** A search result that is not itself a recording — a collection page, say. */
    private fun isAudio(item: JsonNode): Boolean {
        // Absent rather than empty means the record does not describe its formats. The query
        // is already pinned to an audio collection, so that is not grounds to drop it.
        val formats = item["online_format"] ?: return true

        return !formats.isArray || formats.any { it.asText().equals("audio", ignoreCase = true) }
    }

    /**
     * loc.gov gives most fields as arrays but some as a bare string, so one reader covers both.
     */
    private fun firstOf(item: JsonNode, field: String): String? {
        val node = item[field] ?: return null

        val text = when {
            node.isArray -> node.firstOrNull()?.asText()
            node.isValueNode -> node.asText()
            else -> null
        }

        return text?.takeIf { it.isNotBlank() }
    }

    /**
     * `date` is normally the full recording date (`1923-11-07`), but some records carry only
     * the year they are catalogued under, so both are read. `dates` repeats it and stands in
     * where `date` is missing.
     *
     * Showing a 1919 date is the point rather than a problem: ranking stopped sorting on
     * dates, so a collection where every record is a century old is not buried for it. The
     * date is also what decides the licence — see license().
     */
    private fun recordedOn(item: JsonNode): LocalDate? {
        firstOf(item, "date")?.let { raw ->
            raw.toIntOrNull()?.takeIf { it in 1..9999 }?.let { return LocalDate.of(it, 1, 1) }
            runCatching { LocalDate.parse(raw) }.getOrNull()?.let { return it }
        }

        return firstOf(item, "dates")
            ?.substringBefore("T")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    /**
     * Contributors come back lower-cased ("sousa, john philip"). Only the first letter of each
     * word is touched, so a name that already carries interior capitals keeps them.
     */
    private fun displayName(name: String): String =
        name.split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    /**
     * The Jukebox credits "Sony Music Entertainment or EMI Music" on every record it holds,
     * wording that predates the Music Modernization Act — under which a US recording enters
     * the public domain on the first of January 101 years after it was published. So the
     * recording date decides this, not the catalogue note.
     *
     * Past the line we say nothing rather than guess: PUBLIC_DOMAIN would make
     * allowsCommercialUse() true and tell someone they may sell a record that is still
     * somebody's. That is the mistake Icons8 was removed for, and a blanket claim across
     * this collection would have repeated it.
     */
    private fun license(recordedOn: LocalDate): CCLicense =
        if (recordedOn.year <= LocalDate.now(clock).year - YEARS_UNTIL_PUBLIC_DOMAIN) {
            CCLicense.PUBLIC_DOMAIN
        } else {
            CCLicense.UNKNOWN
        }

    private fun performer(item: JsonNode): String? =
        (firstOf(item, "contributor_primary") ?: firstOf(item, "contributor"))
            ?.let { displayName(it) }

    private fun tags(subject: JsonNode?): String = when {
        subject == null -> ""
        subject.isValueNode -> wholeHeadings(heading(subject.asText()))
        // asText() rather than the node itself: a JsonNode stringifies back to JSON, which
        // would carry its quotes into the tag.
        else -> wholeHeadings(subject.take(7).joinToString(", ") { heading(it.asText()) })
    }

    /**
     * The other services cap their tags at 70 characters and let the cut fall where it may.
     * Library headings are long enough to straddle that boundary routinely — "ethnic
     * characterizations" arrives as "ethnic characterizati" — so a trailing fragment is
     * dropped rather than shown. A single heading longer than the budget still gets cut,
     * because showing part of it beats showing none.
     */
    private fun wholeHeadings(tags: String): String {
        if (tags.length <= MAX_TAG_LENGTH) {
            return tags
        }

        val cut = tags.take(MAX_TAG_LENGTH)

        return cut.substringBeforeLast(", ", missingDelimiterValue = cut)
    }

    /**
     * Library subject headings can contain commas of their own — "ragtime, jazz, and more"
     * is one heading, not three. The chips are built by splitting the joined string on
     * commas, so an internal one would break that heading into three fragments, the last of
     * them the word "more".
     */
    private fun heading(subject: String): String =
        subject.split(',').joinToString(" / ") { it.trim() }

    private companion object {
        const val COUNT = 100
        const val MAX_TAG_LENGTH = 70
        const val YEARS_UNTIL_PUBLIC_DOMAIN = 101
        val JUKEBOX_FACET: String = URLEncoder.encode("partof:national jukebox", "UTF-8")
    }
}
