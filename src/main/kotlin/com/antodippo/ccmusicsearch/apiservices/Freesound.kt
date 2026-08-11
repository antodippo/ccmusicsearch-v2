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
import kotlin.math.roundToInt

@Service
class Freesound(private val apiClient: APIClient) : APIService {

    override suspend fun search(query: String): List<SearchResult> {
        val logger = KotlinLogging.logger {}
        val apiKey = System.getProperty("FREESOUND_API_KEY")
        val escapedQuery = URLEncoder.encode(query, "UTF-8")

        val response: HttpResponse<String>
        val tracksArray: JsonNode?
        try {
            val fields = "id,name,username,tags,duration,created,url,license,num_downloads"
            // sort=score is Freesound's relevance order. The duration floor is the only
            // lever that separates pieces of music from the one-shots and loops that make
            // up most of the library — it stays a sample site, so RelevanceRanker also
            // weights it below the music services.
            val filter = URLEncoder.encode("duration:[60 TO *]", "UTF-8")
            response = apiClient.get(URI("https://freesound.org/apiv2/search/text/?token=$apiKey&query=$escapedQuery&sort=score&filter=$filter&fields=$fields&page_size=50"))
            val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
            tracksArray = jsonBody["results"]
        } catch (e: Exception) {
            logger.error { "Error while searching on Freesound: ${e.message}" }
            return emptyList()
        }

        if (tracksArray != null && tracksArray.isArray) {
            return tracksArray.map {
                SearchResult(
                    author = it["username"].asText(),
                    title = it["name"].asText(),
                    duration = it["duration"].toString().toDouble().roundToInt(),
                    bpm = 0,
                    // asText() rather than the node itself: a JsonNode stringifies back to
                    // JSON, which would carry its quotes into the tag.
                    tags = it["tags"].take(7).joinToString(", ") { tag -> tag.asText() }.take(70),
                    date = LocalDate.parse(it["created"].asText().substringBefore("T")),
                    externalLink = URI.create(it["url"].asText()),
                    license = CCLicense.fromUrl(it["license"].asText()),
                    service = SearchService.FREESOUND,
                    popularity = it["num_downloads"]?.asLong()
                )
            }
        }

        return emptyList()
    }
}
