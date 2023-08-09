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
import java.util.*

@Service
class CCMixter(private val apiClient: APIClient, private val printDuration: PrintDuration): APIService {
    override suspend fun search(query: String): Collection<SearchResult> {
        printDuration.startMeasuringDuration(SearchService.CCMIXTER.toString())
        val logger = KotlinLogging.logger {}

        val response : HttpResponse<String>
        try {
            // TODO Solve problem with handshaking :(
            response = apiClient.get(URI("https://ccmixter.org/api/query?limit=20&f=json&tags=$query"))
        } catch (e: Exception) {
            logger.error { "Error while searching on CCMixter: ${e.message}" }
            return emptyList()
        } finally {
            printDuration.finishMeasuringDuration(SearchService.CCMIXTER.toString())
        }

        val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
        if (!jsonBody.isEmpty) {
            return jsonBody.map {
                SearchResult(
                    author = it["user_name"].asText(),
                    title = it["upload_name"].asText(),
                    duration = durationStringToSeconds(it["files"][0]["file_format_info"]["ps"].asText()),
                    bpm = it["upload_extra"]["bpm"].asInt(),
                    tags = it["upload_extra"]["usertags"].asText(),
                    date = LocalDate.parse(
                        it["upload_date_format"].asText(),
                        DateTimeFormatter.ofPattern("E, MMM d, yyyy @ h:mm a", Locale.ENGLISH)
                    ),
                    externalLink = URI.create(it["file_page_url"]?.asText().toString()),
                    license = CCLicense.fromUrl(it["license_url"].asText()),
                    service = SearchService.CCMIXTER
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