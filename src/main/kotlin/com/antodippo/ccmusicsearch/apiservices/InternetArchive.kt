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
class InternetArchive(private val apiClient: APIClient) : APIService {

    private val logger = KotlinLogging.logger {}

    // The archive is mostly not music: lectures, radio shows, podcasts and live sets all
    // live under mediatype:audio. audio_music and netlabels are the two collections that
    // hold released music, and licenseurl:(*http*) drops the "taper permitted" live
    // recordings, which are freely shared but not Creative Commons.
    private val musicOnly =
        "mediatype:(audio) AND collection:(audio_music OR netlabels) AND licenseurl:(*http*)"

    private companion object {
        const val ROWS = 150
    }

    private val fields = listOf(
        "identifier", "title", "creator", "subject",
        "licenseurl", "publicdate", "downloads", "mediatype"
    )

    override suspend fun search(query: String): List<SearchResult> {
        val escapedQuery = URLEncoder.encode("$query AND $musicOnly", "UTF-8")
        val fieldParams = fields.joinToString("") { "&fl%5B%5D=$it" }

        val response: HttpResponse<String>
        val tracksArray: JsonNode?
        try {
            // rows is spent before the mediatype filter and toSearchResult below drop
            // anything unusable, so the archive delivers fewer results than it is asked for
            // — asking for more is what closes that gap rather than what widens the page.
            // No sort parameter: the archive defaults to relevance, which is what we want.
            // mediatype has to be part of q — passing it as its own parameter is rejected
            // with [UNSUPPORTED_VALUE] and there is no "response" key to read back.
            response = apiClient.get(
                URI("https://archive.org/advancedsearch.php?q=$escapedQuery&rows=$ROWS&output=json$fieldParams")
            )
            val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
            tracksArray = jsonBody["response"]["docs"]
        } catch (e: Exception) {
            logger.error { "Error while searching on Internet Archive: ${e.message}" }
            return emptyList()
        }

        if (tracksArray != null && tracksArray.isArray) {
            return tracksArray
                .filter { it["mediatype"]?.asText() == "audio" }
                .mapNotNull { toSearchResult(it) }
        }

        return emptyList()
    }

    /**
     * Null when the item cannot be turned into a result at all.
     *
     * The archive's metadata is filled in by whoever uploaded the item, so every field
     * here is optional in practice however standard it looks: roughly one item in fifty
     * carries no subject, and a few percent give subject as a bare string rather than a
     * list. Anything unusable costs us that one item — never the response, and never
     * the other services, which is what happens if this throws inside SearchEngine's
     * coroutineScope.
     */
    private fun toSearchResult(doc: JsonNode): SearchResult? {
        // The only two without a sensible stand-in: there is nothing to link to without
        // an identifier, and SearchResult.date is not nullable.
        val identifier = doc["identifier"]?.asText() ?: return null
        val publicDate = doc["publicdate"]?.asText() ?: return null

        return try {
            SearchResult(
                author = doc["creator"]?.asText() ?: "",
                title = doc["title"]?.asText() ?: "",
                duration = 0,
                bpm = 0,
                tags = tags(doc["subject"]),
                // Always yyyy-MM-ddTHH:mm:ssZ today, but cutting at the T keeps a change
                // of precision at the other end from costing us the item.
                date = LocalDate.parse(publicDate.substringBefore("T")),
                externalLink = URI.create("https://archive.org/details/$identifier"),
                license = CCLicense.fromUrl(doc["licenseurl"]?.asText() ?: ""),
                service = SearchService.INTERNETARCHIVE,
                popularity = doc["downloads"]?.asLong()
            )
        } catch (e: Exception) {
            logger.warn { "Skipping Internet Archive item $identifier: ${e.message}" }
            null
        }
    }

    private fun tags(subject: JsonNode?): String = when {
        subject == null -> ""
        // Uploaders who typed their subjects into one field instead of several. Without
        // this they iterate as a node with no children and the item shows no tags.
        subject.isTextual -> subject.asText().take(70)
        // asText() rather than the node itself: a JsonNode stringifies back to JSON, which
        // would carry its quotes into the tag.
        else -> subject.take(7).joinToString(", ") { it.asText() }.take(70)
    }
}
