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
                tags = tags(subjects(item)),
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
     * When the thing was recorded, which is what a listener means by its date.
     *
     * Europeana files that under edmTimespan, and the language-aware labels tag it "zxx" —
     * the ISO code for "no linguistic content" — because a date is not a word. `year` is
     * documented but plenty of providers never send it, and timestamp_created is when
     * Europeana ingested the record: a concert given in 2011 was catalogued in 2023, so
     * reading that would put the wrong decade on every row.
     *
     * It stays a last resort rather than a reason to drop the item, since a date we have is
     * better than a row we cannot show at all.
     */
    private fun releaseDate(item: JsonNode): LocalDate? {
        recordedOn(item).forEach { candidate ->
            toLocalDate(candidate)?.let { return it }
        }

        return firstOf(item, "timestamp_created")?.let { toLocalDate(it) }
    }

    /** Every string that might be the recording date, best first. */
    private fun recordedOn(item: JsonNode): List<String> {
        val labelled = item["edmTimespanLabelLangAware"]?.get("zxx")?.map { it.asText() }.orEmpty()
        // edmTimespan mixes the date in with century URIs. The literal one carries a leading
        // '#'; the others simply fail to parse, so both are offered up and sorted out below.
        val raw = item["edmTimespan"]?.map { it.asText().removePrefix("#") }.orEmpty()

        return labelled + raw + listOfNotNull(firstOf(item, "year"))
    }

    private fun toLocalDate(raw: String): LocalDate? {
        raw.toIntOrNull()?.takeIf { it in 1..9999 }?.let { return LocalDate.of(it, 1, 1) }

        return runCatching { LocalDate.parse(raw.substringBefore("T")) }.getOrNull()
    }

    /**
     * Subjects arrive as dcSubjectLangAware, an object keyed by language, and only rarely as
     * a plain dcSubject. English is preferred because the rest of the page is in it; "def"
     * is skipped whatever happens, because that key holds Getty and Europeana URIs rather
     * than words, and a row of vocabulary links makes for useless tags.
     */
    private fun subjects(item: JsonNode): JsonNode? {
        item["dcSubject"]?.let { return it }

        val byLanguage = item["dcSubjectLangAware"] ?: return null
        byLanguage["en"]?.let { return it }

        return byLanguage.properties()
            .firstOrNull { (language, _) -> language != "def" }
            ?.value
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
