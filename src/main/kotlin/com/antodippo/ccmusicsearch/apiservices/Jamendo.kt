package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate

@Service
class Jamendo(private val apiClient: APIClient) : APIService {

    override val service = SearchService.JAMENDO

    private val logger = KotlinLogging.logger {}

    private companion object {
        const val ATTEMPTS = 3
    }

    override suspend fun search(query: String): List<SearchResult> {
        val apiKey = System.getProperty("JAMENDO_API_KEY")
        val escapedQuery = URLEncoder.encode(query, "UTF-8")

        // 200 is the documented ceiling for limit. Jamendo answers an out-of-range
        // limit with an error rather than clamping, which fetchTracks turns into
        // "Jamendo contributed nothing" — visible in the source rail, never fatal.
        // durationbetween keeps out the stingers and idents at one end and the
        // hour-long DJ sets at the other. Jamendo's default response carries no
        // popularity counter; include=stats would add one if we ever want it.
        val uri = URI("https://api.jamendo.com/v3.0/tracks/?client_id=$apiKey&format=jsonpretty&order=relevance&limit=200&durationbetween=60_600&search=$escapedQuery")

        // Jamendo answers a good share of ordinary searches with an empty page: status
        // "success", code 0, no results. Thirty identical requests for "jazz" came back
        // empty six times, on every endpoint and whatever the parameters, and the page that
        // follows an empty one is almost always full — so refreshing the same search showed
        // 0, then 200, then 0. An empty page cannot be told apart from a search that
        // genuinely matches nothing, so both are asked again; for the latter that costs two
        // quick extra requests, still well inside the time the other services take.
        repeat(ATTEMPTS) {
            val tracks = fetchTracks(uri) ?: return emptyList()
            if (!tracks.isEmpty) {
                return tracks.mapNotNull { toSearchResult(it) }
            }
        }

        return emptyList()
    }

    /**
     * The results array — empty when Jamendo found nothing — or null when there is no
     * answer worth asking again for, which has already been logged.
     */
    private suspend fun fetchTracks(uri: URI): JsonNode? {
        val jsonBody: JsonNode
        try {
            // Parsed inside the try so a malformed or empty body costs us Jamendo's results
            // rather than every service's.
            jsonBody = jacksonObjectMapper().readValue(apiClient.get(uri).body())
        } catch (e: Exception) {
            logger.error { "Error while searching on Jamendo: ${e.message}" }
            return null
        }

        // A refusal — bad key, rate limit, bad parameter — also arrives as a 200, with the
        // reason in headers and an empty results array. Without this it was indistinguishable
        // from a search that found nothing.
        val headers = jsonBody["headers"]
        if (headers != null && headers["status"]?.asText() != "success") {
            logger.warn {
                "Jamendo refused the search: code ${headers["code"]?.asText()}, " +
                    "${headers["error_message"]?.asText()}"
            }
            return null
        }

        return jsonBody["results"]?.takeIf { it.isArray }
    }

    /**
     * Null when the track cannot be turned into a result. One unreadable track costs us that
     * track, not the other 199 on the page.
     */
    private fun toSearchResult(track: JsonNode): SearchResult? =
        try {
            SearchResult(
                author = track["artist_name"].asText(),
                title = track["name"].asText(),
                duration = track["duration"].asInt(),
                bpm = 0,
                tags = "",
                date = LocalDate.parse(track["releasedate"].asText()),
                externalLink = URI.create(track["shareurl"].asText()),
                license = CCLicense.fromUrl(track["license_ccurl"].asText()),
                service = SearchService.JAMENDO
            )
        } catch (e: Exception) {
            logger.warn { "Skipping Jamendo track ${track["id"]?.asText()}: ${e.message}" }
            null
        }
}
