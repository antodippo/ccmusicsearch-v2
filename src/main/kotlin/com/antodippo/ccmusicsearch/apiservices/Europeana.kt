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

@Service
class Europeana(private val apiClient: APIClient) : APIService {

    private val logger = KotlinLogging.logger {}

    override suspend fun search(query: String): List<SearchResult> {
        val apiKey = System.getProperty("EUROPEANA_API_KEY")
        val escapedQuery = URLEncoder.encode(query, "UTF-8")

        val response: HttpResponse<String>
        val items: JsonNode?
        try {
            // qf=TYPE:SOUND is what keeps this to audio — Europeana indexes paintings and
            // manuscripts through the same endpoint. reusability=open narrows the licences
            // to Public Domain Mark, CC0, CC BY and CC BY-SA, so nothing here can arrive
            // more restricted than that; the licence is still read per item rather than
            // assumed. rows tops out at 100. No sort parameter: relevance is the default.
            response = apiClient.get(
                URI(
                    "https://api.europeana.eu/record/v2/search.json?wskey=$apiKey" +
                        "&query=$escapedQuery&qf=TYPE%3ASOUND&reusability=open&rows=100&profile=rich"
                )
            )
            val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
            items = jsonBody["items"]
        } catch (e: Exception) {
            logger.error { "Error while searching on Europeana: ${e.message}" }
            return emptyList()
        }

        if (items != null && items.isArray) {
            return items.mapNotNull { toSearchResult(it) }
        }

        return emptyList()
    }

    /**
     * Null when the item cannot be turned into a result at all.
     *
     * Europeana aggregates thousands of institutions, each cataloguing to its own standard,
     * so every field here is optional in practice however standard it looks. Anything
     * unusable costs us that one item — never the response, and never the other services,
     * which is what happens if this throws inside SearchEngine's coroutineScope.
     */
    private fun toSearchResult(item: JsonNode): SearchResult? {
        // The only two without a sensible stand-in: there is nothing to link to without an
        // id, and SearchResult.date is not nullable.
        val id = firstOf(item, "id") ?: return null
        val date = releaseDate(item) ?: return null

        return try {
            SearchResult(
                // Heritage records often name the holding institution but not a performer.
                // SongView.hasAuthor already drops the separator on a blank byline, so an
                // archive is a better answer than nothing and nothing beats a wrong guess.
                author = firstOf(item, "dcCreator") ?: firstOf(item, "dataProvider") ?: "",
                title = firstOf(item, "title") ?: "",
                // Neither is in the search response. Duration lives on the record's web
                // resources, which would be a second request per item; the length filter
                // already skips results reporting zero, as it does for Internet Archive.
                duration = 0,
                bpm = 0,
                tags = tags(item["dcSubject"]),
                date = date,
                // Built from the id rather than the guid Europeana also sends: that one
                // carries API tracking parameters we have no business putting in a link.
                externalLink = URI.create("https://www.europeana.eu/item$id"),
                license = CCLicense.fromUrl(
                    firstOf(item, "edmRights") ?: firstOf(item, "rights") ?: ""
                ),
                service = SearchService.EUROPEANA
            )
        } catch (e: Exception) {
            logger.warn { "Skipping Europeana item $id: ${e.message}" }
            null
        }
    }

    /**
     * Europeana returns most metadata as arrays — one entry per language, or per record
     * merged into the item — but some fields arrive as a bare string. One reader covers
     * both rather than each call site guessing which it got.
     */
    private fun firstOf(item: JsonNode, field: String): String? {
        val node = item[field] ?: return null

        val text = when {
            node.isArray -> node.firstOrNull()?.asText()
            node.isValueNode -> node.asText()
            else -> null
        }

        return text?.takeIf { it.isNotBlank() }
    }

    /**
     * `year` is what these archives actually record, and for a heritage recording the year
     * is the useful thing to show. It is only ever a year, so the first of January stands
     * in for the rest of it. Ranking no longer sorts on dates, so a 1924 recording is not
     * buried for being old — it just reads as 1924.
     *
     * Items without a parseable year fall back to when Europeana ingested them, which is
     * always present and is at least true, if less interesting.
     */
    private fun releaseDate(item: JsonNode): LocalDate? {
        firstOf(item, "year")
            ?.toIntOrNull()
            ?.takeIf { it in 1..9999 }
            ?.let { return LocalDate.of(it, 1, 1) }

        return firstOf(item, "timestamp_created")
            ?.substringBefore("T")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    private fun tags(subject: JsonNode?): String = when {
        subject == null -> ""
        // Cataloguers who typed their subjects into one field instead of several.
        subject.isValueNode -> subject.asText().take(70)
        // asText() rather than the node itself: a JsonNode stringifies back to JSON, which
        // would carry its quotes into the tag.
        else -> subject.take(7).joinToString(", ") { it.asText() }.take(70)
    }
}
