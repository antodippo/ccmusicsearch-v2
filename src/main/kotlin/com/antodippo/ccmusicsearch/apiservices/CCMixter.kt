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
    override suspend fun search(query: String): Collection<SearchResult> {
        val logger = KotlinLogging.logger {}
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
            return jsonBody.map {
                SearchResult(
                    author = it["user_name"].asText(),
                    title = it["upload_name"].asText(),
                    duration = durationStringToSeconds(it["files"][0]["file_format_info"]["ps"].asText()),
                    bpm = it["upload_extra"]["bpm"].asInt(),
                    tags = it["upload_extra"]["usertags"].asText().take(70),
                    date = LocalDate.parse(
                        it["upload_date_format"].asText(),
                        DateTimeFormatter.ofPattern("E, MMM d, yyyy @ h:mm a", Locale.ENGLISH)
                    ),
                    externalLink = URI.create(it["file_page_url"]?.asText().toString()),
                    license = CCLicense.fromUrl(it["license_url"].asText()),
                    service = SearchService.CCMIXTER,
                    popularity = it["upload_num_scores"]?.asLong()
                )
            }
        }

        return emptyList()
    }

    private fun durationStringToSeconds(durationString: String): Int {
        val stringParts = durationString.split(":")
        if (stringParts.size != 2) {
            return 0
        }
        return stringParts[0].toInt() * 60 + stringParts[1].toInt()
    }
}