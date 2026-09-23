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
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class CCMixter(private val apiClient: APIClient): APIService {

    override val service = SearchService.CCMIXTER

    private val logger = KotlinLogging.logger {}

    override suspend fun search(query: String): Collection<SearchResult> {
        val escapedQuery = URLEncoder.encode(query, "UTF-8")

        val jsonBody: JsonNode
        try {
            // TODO Solve problem with handshaking :(
            // search= rather than tags=: it matches title and artist as well as tags, so
            // "jazz" finds "Jazzy Parts", which a tag-only query misses. No sort parameter,
            // which leaves ccMixter's own relevance order in place.
            // 25 is the ceiling — ask for more and ccMixter answers 200 with an empty body.
            val response = apiClient.get(URI("https://ccmixter.org/api/query?limit=25&f=json&search=$escapedQuery"))
            // Parsed inside the try: an empty body is a real response from this API, and
            // letting it throw here would take down every other service's results too.
            jsonBody = jacksonObjectMapper().readValue(response.body())
        } catch (e: Exception) {
            logger.error { "Error while searching on CCMixter: ${e.message}" }
            return emptyList()
        }

        if (!jsonBody.isEmpty) {
            return jsonBody.mapNotNull { toSearchResult(it) }
        }

        return emptyList()
    }

    /**
     * Null when the upload cannot be turned into a result at all.
     *
     * Not every upload is a track: stem packs and sample kits arrive as a single zip, and at
     * least one upload's only file is its cover image. None of those carry a playing time,
     * and reading it unguarded threw on the first one — which, under SearchEngine's backstop,
     * cost every other ccMixter result for the search as well.
     */
    private fun toSearchResult(upload: JsonNode): SearchResult? =
        try {
            SearchResult(
                author = upload["user_name"].asText(),
                title = upload["upload_name"].asText(),
                duration = duration(upload["files"]),
                bpm = upload["upload_extra"]?.get("bpm")?.asInt() ?: 0,
                tags = upload["upload_extra"]?.get("usertags")?.asText()?.take(70) ?: "",
                date = LocalDate.parse(
                    upload["upload_date_format"].asText(),
                    DateTimeFormatter.ofPattern("E, MMM d, yyyy @ h:mm a", Locale.ENGLISH)
                ),
                externalLink = URI.create(upload["file_page_url"]?.asText().toString()),
                license = CCLicense.fromUrl(upload["license_url"].asText()),
                service = SearchService.CCMIXTER,
                popularity = upload["upload_num_scores"]?.asLong()
            )
        } catch (e: Exception) {
            logger.warn { "Skipping ccMixter upload ${upload["upload_id"]?.asText()}: ${e.message}" }
            null
        }

    /**
     * The playing time of the first file that has one, or 0 — which the length filter
     * already treats as "unknown", as it does for every Internet Archive result. The stems
     * are still worth listing; they are simply not timed.
     */
    private fun duration(files: JsonNode?): Int =
        files
            ?.firstNotNullOfOrNull { it["file_format_info"]?.get("ps")?.asText() }
            ?.let { durationStringToSeconds(it) }
            ?: 0

    private fun durationStringToSeconds(durationString: String): Int {
        val stringParts = durationString.split(":")
        if (stringParts.size != 2) {
            return 0
        }
        return stringParts[0].toInt() * 60 + stringParts[1].toInt()
    }
}