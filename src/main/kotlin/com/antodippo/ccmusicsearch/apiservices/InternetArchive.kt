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
import java.time.format.DateTimeFormatter

@Service
class InternetArchive(private val apiClient: APIClient) : APIService {

    override suspend fun search(query: String): List<SearchResult> {
        val logger = KotlinLogging.logger {}
        val escapedQuery = URLEncoder.encode(query, "UTF-8")

        val response: HttpResponse<String>
        val tracksArray: JsonNode?
        try {
            response = apiClient.get(URI("https://archive.org/advancedsearch.php?q=subject:$escapedQuery&rows=20&output=json&mediatype=audio&sort=createdate+desc"))
            val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
            tracksArray = jsonBody["response"]["docs"]
        } catch (e: Exception) {
            logger.error { "Error while searching on Internet Archive: ${e.message}" }
            return emptyList()
        }

        if (tracksArray != null && tracksArray.isArray) {
            return tracksArray
                .filter { it["mediatype"].asText() == "audio" }
                .map {
                    SearchResult(
                        author = it["creator"]?.asText() ?: "",
                        title = it["title"]?.asText() ?: "",
                        duration = 0,
                        bpm = 0,
                        tags = it["subject"].take(7).joinToString(", ").take(70),
                        date = LocalDate.parse(
                            it["publicdate"].asText(),
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                        ),
                        externalLink = URI.create("https://archive.org/search?query=${it["identifier"].asText()}"),
                        license = CCLicense.fromUrl(it["licenseurl"]?.asText() ?: ""),
                        service = SearchService.INTERNETARCHIVE
                    )
            }
        }

        return emptyList()
    }
}
