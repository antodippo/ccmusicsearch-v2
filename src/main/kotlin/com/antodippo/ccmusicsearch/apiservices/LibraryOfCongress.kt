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
import java.time.LocalDate

@Service
class LibraryOfCongress(private val apiClient: APIClient) : APIService {

    private val logger = KotlinLogging.logger {}

    override suspend fun search(query: String): List<SearchResult> {
        val escapedQuery = URLEncoder.encode(query, "UTF-8")

        val response: HttpResponse<String>
        val items: JsonNode?
        try {
            // Scoped to the National Jukebox rather than to loc.gov/audio at large, and that
            // scoping is what makes the hard-coded licence below honest: the Jukebox is
            // recordings from 1900-1925, and everything published before 1923 entered the
            // public domain under the Music Modernization Act. The wider audio endpoint mixes
            // in material that is still in copyright, which we would have no way to tell apart
            // — loc.gov states rights as free prose, not as a licence URL.
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
                author = firstOf(item, "contributor")?.let { displayName(it) } ?: "",
                title = firstOf(item, "title") ?: "",
                // Not in the search response; it lives on the item record, which would be a
                // second request each. The length filter already skips results reporting
                // zero, as it does for Internet Archive and Europeana.
                duration = 0,
                bpm = 0,
                tags = tags(item["subject"]),
                date = date,
                externalLink = URI.create(link),
                // Every recording in this collection is public domain, so unlike Icons8's old
                // blanket UNKNOWN this states something true rather than papering over a gap.
                // It holds only because the query above is pinned to the Jukebox.
                license = CCLicense.PUBLIC_DOMAIN,
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
     * `date` is usually the bare year these recordings are catalogued under, which is the
     * useful thing to show for a 1917 record — ranking no longer sorts on dates, so an old
     * one is not buried for being old. `dates` carries a full timestamp where there is one.
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

    private fun tags(subject: JsonNode?): String = when {
        subject == null -> ""
        subject.isValueNode -> subject.asText().take(70)
        // asText() rather than the node itself: a JsonNode stringifies back to JSON, which
        // would carry its quotes into the tag.
        else -> subject.take(7).joinToString(", ") { it.asText() }.take(70)
    }

    private companion object {
        const val COUNT = 100
        val JUKEBOX_FACET: String = URLEncoder.encode("partof:national jukebox", "UTF-8")
    }
}
