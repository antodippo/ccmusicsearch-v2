package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpResponse
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

@Service
class Icons8(private val apiClient: APIClient) : APIService {

    override suspend fun search(query: String): List<SearchResult> {
        val logger = KotlinLogging.logger {}
        val apiKey = System.getProperty("ICONS8_API_KEY")

        val response: HttpResponse<String>
        val tracksArray: JsonNode?
        try {
            response = apiClient.get(URI("https://api-music.icons8.com/api/v2/tracks?token=$apiKey&search=$query&perPage=20"))
            val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
            tracksArray = jsonBody["tracks"]
        } catch (e: Exception) {
            logger.error { "Error while searching on Icons8: ${e.message}" }
            return emptyList()
        }

        if (tracksArray != null && tracksArray.isArray) {
            return tracksArray.map {
                SearchResult(
                    author = it["artist"].asText(),
                    title = it["name"].asText(),
                    duration = it["duration"].toString().toDouble().roundToInt(),
                    bpm = it["bpm"].asInt(),
                    tags = it["tags"].take(7).joinToString(", ").take(70),
                    date = this.getLocalDate(it["createdAt"].asText()),
                    externalLink = URI.create(it["preview"]["url"].asText()),
                    license = CCLicense.UNKNOWN,
                    service = SearchService.ICONS8
                )
            }
        }

        return emptyList()
    }

    private fun getLocalDate(timestamp: String): LocalDate {
        val unixTimestamp = timestamp.toLong()
        val instant = Instant.ofEpochMilli(unixTimestamp)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())

        return localDateTime.toLocalDate()
    }
}
