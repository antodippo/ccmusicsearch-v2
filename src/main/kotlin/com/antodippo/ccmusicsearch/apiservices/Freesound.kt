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
import kotlin.math.roundToInt

@Service
class Freesound(private val apiClient: APIClient, private val printDuration: PrintDuration) : APIService {

    override suspend fun search(query: String): List<SearchResult> {
        printDuration.startMeasuringDuration(SearchService.FREESOUND.toString())
        val logger = KotlinLogging.logger {}
        val apiKey = System.getProperty("FREESOUND_API_KEY")

        val response: HttpResponse<String>
        val tracksArray: JsonNode?
        try {
            val fields = "id,name,username,tags,duration,created,url,license"
            response = apiClient.get(URI("https://freesound.org/apiv2/search/text/?token=$apiKey&query=$query&sort=created_desc&fields=$fields&page_size=5"))
            val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
            tracksArray = jsonBody["results"]
        } catch (e: Exception) {
            logger.error { "Error while searching on Internet Archive: ${e.message}" }
            return emptyList()
        } finally {
            printDuration.finishMeasuringDuration(SearchService.FREESOUND.toString())
        }

        if (tracksArray!!.isArray) {
            return tracksArray.map {
                SearchResult(
                    author = it["username"].asText(),
                    title = it["name"].asText(),
                    duration = it["duration"].toString().toDouble().roundToInt(),
                    bpm = 0,
                    tags = it["tags"].take(7).joinToString(", "),
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
