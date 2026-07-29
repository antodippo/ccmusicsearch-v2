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

    // The archive is mostly not music: lectures, radio shows, podcasts and live sets all
    // live under mediatype:audio. audio_music and netlabels are the two collections that
    // hold released music, and licenseurl:(*http*) drops the "taper permitted" live
    // recordings, which are freely shared but not Creative Commons.
    private val musicOnly =
        "mediatype:(audio) AND collection:(audio_music OR netlabels) AND licenseurl:(*http*)"

    private val fields = listOf(
        "identifier", "title", "creator", "subject",
        "licenseurl", "publicdate", "downloads", "mediatype"
    )

    override suspend fun search(query: String): List<SearchResult> {
        val logger = KotlinLogging.logger {}
        val escapedQuery = URLEncoder.encode("$query AND $musicOnly", "UTF-8")
        val fieldParams = fields.joinToString("") { "&fl%5B%5D=$it" }

        val response: HttpResponse<String>
        val tracksArray: JsonNode?
        try {
            // No sort parameter: the archive defaults to relevance, which is what we want.
            // mediatype has to be part of q — passing it as its own parameter is rejected
            // with [UNSUPPORTED_VALUE] and there is no "response" key to read back.
            response = apiClient.get(
                URI("https://archive.org/advancedsearch.php?q=$escapedQuery&rows=50&output=json$fieldParams")
            )
            val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
            tracksArray = jsonBody["response"]["docs"]
        } catch (e: Exception) {
            logger.error { "Error while searching on Internet Archive: ${e.message}" }
            return emptyList()
        }

        if (tracksArray != null && tracksArray.isArray) {
            return tracksArray
                .filter { it["mediatype"]?.asText() == "audio" }
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
                        externalLink = URI.create("https://archive.org/details/${it["identifier"].asText()}"),
                        license = CCLicense.fromUrl(it["licenseurl"]?.asText() ?: ""),
                        service = SearchService.INTERNETARCHIVE,
                        popularity = it["downloads"]?.asLong()
                    )
            }
        }

        return emptyList()
    }
}
