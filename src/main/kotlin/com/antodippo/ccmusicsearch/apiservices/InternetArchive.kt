package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.*
import com.antodippo.ccmusicsearch.infra.PrintDuration
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class InternetArchive(private val apiClient: APIClient, private val printDuration: PrintDuration) : APIService {

    override suspend fun search(query: String): List<SearchResult> {
        printDuration.startMeasuringDuration(SearchService.INTERNETARCHIVE.toString())
        val logger = KotlinLogging.logger {}

        val response: HttpResponse<String>
        val tracksArray: JsonNode?
        try {
            response = apiClient.get(URI("https://archive.org/advancedsearch.php?q=subject:$query&rows=10&output=json&mediatype=audio&sort=createdate+desc"))
            val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
            tracksArray = jsonBody["response"]["docs"]
        } catch (e: Exception) {
            logger.error { "Error while searching on Internet Archive: ${e.message}" }
            return emptyList()
        } finally {
            printDuration.finishMeasuringDuration(SearchService.INTERNETARCHIVE.toString())
        }

        if (tracksArray!!.isArray) {
            return tracksArray.map {
                SearchResult(
                    author = it["creator"]?.asText() ?: "",
                    title = it["title"]?.asText() ?: "",
                    duration = 0,
                    bpm = 0,
                    tags = it["subject"].take(7).joinToString(", "),
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
