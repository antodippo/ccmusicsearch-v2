package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpResponse
import java.time.LocalDate

@Service
class Jamendo(private val apiClient: APIClient) : APIService {

    override suspend fun search(query: String): List<SearchResult> {
        val logger = KotlinLogging.logger {}
        val apiKey = System.getProperty("JAMENDO_API_KEY")

        val response : HttpResponse<String>
        try {
            response = apiClient.get(URI("https://api.jamendo.com/v3.0/tracks/?client_id=$apiKey&format=jsonpretty&order=releasedate_desc&limit=20&search=$query"))
        } catch (e: Exception) {
            logger.error { "Error while searching on Jamendo: ${e.message}" }
            return emptyList()
        }

        val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
        if (!jsonBody.isEmpty && jsonBody["results"] != null) {
            return jsonBody["results"].map {
                SearchResult(
                    author = it["artist_name"].asText(),
                    title = it["name"].asText(),
                    duration = it["duration"].asInt(),
                    bpm = 0,
                    tags = "",
                    date = LocalDate.parse(it["releasedate"].asText()),
                    externalLink = URI.create(it["shareurl"].asText()),
                    license = CCLicense.fromUrl(it["license_ccurl"].asText()),
                    service = SearchService.JAMENDO
                )
            }
        }

        return emptyList()
    }
}