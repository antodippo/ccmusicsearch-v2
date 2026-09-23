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
import java.text.Normalizer
import java.time.LocalDate

@Service
class InternetArchive(private val apiClient: APIClient) : APIService {

    override val service = SearchService.INTERNETARCHIVE

    private val logger = KotlinLogging.logger {}

    // The archive is mostly not music: lectures, radio shows, podcasts and live sets all
    // live under mediatype:audio. audio_music and netlabels are the two collections that
    // hold released music, and licenseurl:(*http*) drops the "taper permitted" live
    // recordings, which are freely shared but not Creative Commons.
    private val musicOnly =
        "mediatype:(audio) AND collection:(audio_music OR netlabels) AND licenseurl:(*http*)"

    private companion object {
        const val ROWS = 150
    }

    private val fields = listOf(
        "identifier", "title", "creator", "subject",
        "licenseurl", "publicdate", "downloads", "mediatype"
    )

    override suspend fun search(query: String): List<SearchResult> {
        // Nothing left to look for once the query syntax is taken out — "*", a lone quote —
        // and sending an empty group would only get the request rejected.
        val archiveQuery = archiveQuery(query) ?: return emptyList()
        val escapedQuery = URLEncoder.encode("$archiveQuery AND $musicOnly", "UTF-8")
        val fieldParams = fields.joinToString("") { "&fl%5B%5D=$it" }

        val response: HttpResponse<String>
        val tracksArray: JsonNode?
        try {
            // rows is spent before the mediatype filter and toSearchResult below drop
            // anything unusable, so the archive delivers fewer results than it is asked for
            // — asking for more is what closes that gap rather than what widens the page.
            // No sort parameter: the archive defaults to relevance, which is what we want.
            // mediatype has to be part of q — passing it as its own parameter is rejected
            // with [UNSUPPORTED_VALUE] and there is no "response" key to read back.
            response = apiClient.get(
                URI("https://archive.org/advancedsearch.php?q=$escapedQuery&rows=$ROWS&output=json$fieldParams")
            )
            val jsonBody = jacksonObjectMapper().readValue<JsonNode>(response.body())
            // A query the archive will not run still comes back 200, as {"error": "…"} with
            // no "response" key. Reading straight through to docs turned that into an NPE
            // whose message said nothing about why.
            jsonBody["error"]?.let {
                logger.error { "Internet Archive rejected the query: ${it.asText()}" }
                return emptyList()
            }
            tracksArray = jsonBody["response"]?.get("docs")
        } catch (e: Exception) {
            logger.error { "Error while searching on Internet Archive: ${e.message}" }
            return emptyList()
        }

        if (tracksArray != null && tracksArray.isArray) {
            return tracksArray
                .filter { it["mediatype"]?.asText() == "audio" }
                .mapNotNull { toSearchResult(it) }
        }

        return emptyList()
    }

    /**
     * Null when the item cannot be turned into a result at all.
     *
     * The archive's metadata is filled in by whoever uploaded the item, so every field
     * here is optional in practice however standard it looks: roughly one item in fifty
     * carries no subject, and a few percent give subject as a bare string rather than a
     * list. Anything unusable costs us that one item — never the response, and never
     * the other services, which is what happens if this throws inside SearchEngine's
     * coroutineScope.
     */
    private fun toSearchResult(doc: JsonNode): SearchResult? {
        // The only two without a sensible stand-in: there is nothing to link to without
        // an identifier, and SearchResult.date is not nullable.
        val identifier = doc["identifier"]?.asText() ?: return null
        val publicDate = doc["publicdate"]?.asText() ?: return null

        return try {
            SearchResult(
                author = doc["creator"]?.asText() ?: "",
                title = doc["title"]?.asText() ?: "",
                duration = 0,
                bpm = 0,
                tags = tags(doc["subject"]),
                // Always yyyy-MM-ddTHH:mm:ssZ today, but cutting at the T keeps a change
                // of precision at the other end from costing us the item.
                date = LocalDate.parse(publicDate.substringBefore("T")),
                externalLink = URI.create("https://archive.org/details/$identifier"),
                license = CCLicense.fromUrl(doc["licenseurl"]?.asText() ?: ""),
                service = SearchService.INTERNETARCHIVE,
                popularity = doc["downloads"]?.asLong()
            )
        } catch (e: Exception) {
            logger.warn { "Skipping Internet Archive item $identifier: ${e.message}" }
            null
        }
    }

    private fun tags(subject: JsonNode?): String = when {
        subject == null -> ""
        // Uploaders who typed their subjects into one field instead of several. Without
        // this they iterate as a node with no children and the item shows no tags.
        subject.isTextual -> subject.asText().take(70)
        // asText() rather than the node itself: a JsonNode stringifies back to JSON, which
        // would carry its quotes into the tag.
        else -> subject.take(7).joinToString(", ") { it.asText() }.take(70)
    }
}

// \p classes rather than \w: Java's \w is ASCII-only, so it would blank out 日本 or Cyrillic,
// and without \p{M} Thai and Devanagari words fall apart at their combining marks.
private val NOT_PART_OF_A_WORD = Regex("""[^\p{L}\p{M}\p{N}'-]""")
private val UNATTACHED_JOINER = Regex("""(?<![\p{L}\p{M}\p{N}])['-]|['-](?![\p{L}\p{M}\p{N}])""")
private val NEGATED_WORD = Regex("""^-[\p{L}\p{M}\p{N}]""")
private val WHITESPACE = Regex("""\s+""")
private val OPERATORS = setOf("AND", "OR", "NOT")

/**
 * What someone typed, rewritten into a query the archive will parse, or null when nothing
 * searchable is left.
 *
 * The archive parses q as query syntax, and anything it cannot parse comes back as an error
 * rather than as results. Real searches trip it all the time: "ac/dc", "csma/cd", an
 * unbalanced quote or bracket, a pasted URL, a trailing OR. Escaping the reserved characters
 * is not the way out — the archive's rewriter rejects `\-` and `\+` too, losing "hip-hop"
 * and "c++" — and neither is flattening everything to bare words, which turns "jazz -live"
 * into jazz *and* live and makes OR a required word. So the syntax that does work is kept
 * and only what cannot parse is dropped:
 *
 * - words, with hyphens and apostrophes kept only inside one ("hip-hop", "don't");
 * - a leading `-` on a word, which excludes it;
 * - AND and OR with a term on either side, and NOT with a term after it — anywhere else
 *   they are dropped, and an operator word that is part of other text is just a word;
 * - "quoted phrases", as long as the quotes pair up. An odd one out cannot be paired, so
 *   then none of them are trusted.
 *
 * Everything else is a space. NFC because a decomposed "café" finds nothing where the
 * composed one does.
 */
internal fun archiveQuery(query: String): String? {
    val text = Normalizer.normalize(query, Normalizer.Form.NFC).replace('’', '\'')
    val parts = if (text.count { it == '"' } % 2 == 0) text.split('"') else listOf(text.replace('"', ' '))

    // Even parts sit outside quotes, odd ones inside a pair.
    val items = parts.flatMapIndexed { index, part ->
        if (index % 2 == 1) listOfNotNull(phrase(part)) else looseTerms(part)
    }

    val kept = mutableListOf<String>()
    items.forEachIndexed { index, item ->
        val next = items.getOrNull(index + 1)
        val keep = when (item) {
            "AND", "OR" -> kept.isNotEmpty() && next != null && next !in OPERATORS
            "NOT" -> next != null && next !in OPERATORS && !next.startsWith("-")
            else -> true
        }
        if (keep) kept.add(item)
    }

    return if (kept.isEmpty()) null else kept.joinToString(" ", prefix = "(", postfix = ")")
}

private fun looseTerms(text: String): List<String> =
    text.split(WHITESPACE).filter { it.isNotEmpty() }.flatMap { raw ->
        if (raw in OPERATORS) {
            return@flatMap listOf(raw)
        }

        val words = words(raw)
        if (words.isNotEmpty() && NEGATED_WORD.containsMatchIn(raw)) {
            listOf("-" + words.first()) + words.drop(1)
        } else {
            words
        }
    }

private fun phrase(text: String): String? =
    words(text).takeIf { it.isNotEmpty() }?.joinToString(" ", prefix = "\"", postfix = "\"")

/**
 * The words in a stretch of text, everything between them treated as a space. An operator
 * name found here is part of other text — "AND," or a phrase — so it is lower-cased into a
 * plain word rather than left to act as an operator.
 */
private fun words(text: String): List<String> =
    text.replace(NOT_PART_OF_A_WORD, " ")
        .replace(UNATTACHED_JOINER, " ")
        .split(' ')
        .filter { it.isNotEmpty() }
        .map { if (it in OPERATORS || it == "TO") it.lowercase() else it }
