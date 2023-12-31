package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
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
            val fields = "id,name,username,tags,duration,created,url,license"
            response = apiClient.get(URI("https://freesound.org/apiv2/search/text/?token=$apiKey&query=$escapedQuery&sort=created_desc&fields=$fields&page_size=5"))
            val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
            tracksArray = jsonBody["results"]
        } catch (e: Exception) {
            logger.error { "Error while searching on Internet Archive: ${e.message}" }
            return emptyList()
        }

        if (tracksArray != null && tracksArray.isArray) {
            return tracksArray.map {
                SearchResult(
                    author = it["username"].asText(),
                    title = it["name"].asText(),
                    duration = it["duration"].toString().toDouble().roundToInt(),
                    bpm = 0,
                    tags = it["tags"].take(7).joinToString(", ").take(70),
                    date = LocalDate.parse(it["created"].asText().substringBefore("T")),
                    externalLink = URI.create(it["url"].asText()),
                    license = CCLicense.fromUrl(it["license"].asText()),
                    service = SearchService.FREESOUND
                )
            }
        }

        return emptyList()
    }
}
