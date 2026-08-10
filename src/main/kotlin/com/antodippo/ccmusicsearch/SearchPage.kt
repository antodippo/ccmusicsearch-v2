package com.antodippo.ccmusicsearch

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything the search template needs, already formatted.
 *
 * Mustache is logic-less on purpose, so every label, CSS class and filter attribute is
 * decided here rather than in the template or in the browser. The page's filtering and
 * sorting run client-side over the whole result set, which is why each row also carries
 * its raw values as data attributes.
 */
data class SearchPage(
    val q: String,
    val hasQuery: Boolean,
    val hasResults: Boolean,
    val resultCount: Int,
    val resultNoun: String,
    val sourceCount: Int,
    val sourceNoun: String,
    val pageTitle: String,
    val metaDescription: String,
    val results: List<SongView>,
    val facets: List<FacetView>,
    val licences: List<LicenceView>,
    val length: RangeView,
    val tempo: RangeView,
) {
    companion object {

        fun from(query: String?, results: List<SearchResult>): SearchPage {
            val songs = results.mapIndexed { index, result -> SongView.of(result, index) }
            val sourceCount = results.map { it.service }.distinct().size
            val resultNoun = plural(songs.size, "result")
            val sourceNoun = plural(sourceCount, "source")

            return SearchPage(
                q = query.orEmpty(),
                hasQuery = query != null,
                hasResults = songs.isNotEmpty(),
                resultCount = songs.size,
                resultNoun = resultNoun,
                sourceCount = sourceCount,
                sourceNoun = sourceNoun,
                pageTitle = pageTitle(query, songs.size),
                metaDescription = metaDescription(query, songs.size, resultNoun, sourceCount, sourceNoun),
                results = songs,
                facets = facets(results),
                licences = licences(results),
                length = lengthRange(results),
                tempo = tempoRange(results),
            )
        }

        /**
         * The landing page leads with "Free … Music Search" because that is the phrase people
         * type; "Creative Commons" qualifies it rather than leading. Calling any of this
         * "royalty-free" would be untrue — half the catalogue is NC-licensed — and a title that
         * oversells buys a click and loses the visitor.
         *
         * Search pages carry noindex, so their title is for the tab and for shared links.
         */
        private fun pageTitle(query: String?, resultCount: Int): String = when {
            query == null -> "Free Creative Commons Music Search — CCMusic Search"
            resultCount == 0 -> "No Creative Commons music found for “$query”"
            else -> "“$query” — free Creative Commons music"
        }

        private fun metaDescription(
            query: String?,
            resultCount: Int,
            resultNoun: String,
            sourceCount: Int,
            sourceNoun: String,
        ): String = when {
            query == null ->
                "Search Jamendo, ccMixter, Icons8, Internet Archive and Freesound at once for " +
                    "free, Creative Commons-licensed music for videos, podcasts and streams. " +
                    "Filter by licence, length and BPM."

            resultCount == 0 ->
                "No Creative Commons music matched “$query”. Try a different word, an artist " +
                    "name, or a mood."

            else ->
                "$resultCount Creative Commons $resultNoun for “$query” across $sourceCount " +
                    "$sourceNoun. Filter by licence, length and BPM, and check what each track " +
                    "allows before you use it."
        }

        /** Every service is listed whether or not it answered, so the rail keeps its shape. */
        private fun facets(results: List<SearchResult>): List<FacetView> {
            val counts = results.groupingBy { it.service }.eachCount()

            return SearchService.values().map { service ->
                val count = counts[service] ?: 0
                FacetView(
                    key = service.toString(),
                    label = service.label(),
                    count = count,
                    enabled = count > 0,
                )
            }
        }

        private fun licences(results: List<SearchResult>): List<LicenceView> {
            val present = results.map { it.license }.toSet()

            return CCLicense.values()
                .filter { it in present }
                .map { LicenceView(key = it.name, label = it.label()) }
        }

        private fun lengthRange(results: List<SearchResult>): RangeView {
            val durations = results.map { it.duration }.filter { it > 0 }
            if (durations.isEmpty()) {
                return RangeView(30, 600, "0:30", "10:00", "0:30 – 10:00", available = false)
            }

            val floor = roundDown(durations.min(), STEP_SECONDS)
            val ceiling = roundUp(durations.max(), STEP_SECONDS).coerceAtLeast(floor + STEP_SECONDS)

            return RangeView(
                floor = floor,
                ceiling = ceiling,
                floorLabel = durationLabel(floor),
                ceilingLabel = durationLabel(ceiling),
                valueLabel = "${durationLabel(floor)} – ${durationLabel(ceiling)}",
                available = true,
            )
        }

        /**
         * Only ccMixter and Icons8 report a tempo; everything else sends 0. Tracks without
         * one are never filtered out, so a missing BPM is not a reason to hide a result.
         */
        private fun tempoRange(results: List<SearchResult>): RangeView {
            val tempos = results.map { it.bpm }.filter { it > 0 }
            if (tempos.isEmpty()) {
                return RangeView(60, 180, "60", "180", "60 – 180 BPM", available = false)
            }

            val floor = roundDown(tempos.min(), STEP_BPM)
            val ceiling = roundUp(tempos.max(), STEP_BPM).coerceAtLeast(floor + STEP_BPM)

            return RangeView(
                floor = floor,
                ceiling = ceiling,
                floorLabel = floor.toString(),
                ceilingLabel = ceiling.toString(),
                valueLabel = "$floor – $ceiling BPM",
                available = true,
            )
        }

        /** The client-side filters re-count as they go, so they pluralise the same way. */
        private fun plural(count: Int, noun: String) = if (count == 1) noun else noun + "s"

        private const val STEP_SECONDS = 15
        private const val STEP_BPM = 10

        private fun roundDown(value: Int, step: Int) = value / step * step
        private fun roundUp(value: Int, step: Int) = (value + step - 1) / step * step
    }
}

/** One result row, in the shape the template renders it. */
data class SongView(
    val title: String,
    val author: String,
    val hasAuthor: Boolean,
    val dateLabel: String,
    val dateIso: String,
    val tagList: List<String>,
    val hasTags: Boolean,
    val serviceKey: String,
    val serviceLabel: String,
    val licenceKey: String,
    val licenceLabel: String,
    val licenceUrl: String,
    val commercialUseAllowed: Boolean,
    val bpm: Int,
    val bpmLabel: String,
    val duration: Int,
    val durationLabel: String,
    val popularityValue: Long,
    val popularityLabel: String,
    val hasPopularity: Boolean,
    val externalLink: String,
    val discClass: String,
    val position: Int,
) {
    companion object {
        fun of(result: SearchResult, index: Int): SongView {
            val tags = tagList(result.tags)

            return SongView(
                title = result.title,
                author = result.author,
                // Archive uploads often have no creator; the byline drops the separator
                // rather than opening with a stray dot.
                hasAuthor = result.author.isNotBlank(),
                dateLabel = result.date.format(MONTH_YEAR),
                dateIso = result.date.toString(),
                tagList = tags,
                hasTags = tags.isNotEmpty(),
                serviceKey = result.service.toString(),
                serviceLabel = result.service.label(),
                licenceKey = result.license.name,
                licenceLabel = result.license.label(),
                licenceUrl = result.licenseUrl.toString(),
                commercialUseAllowed = result.license.allowsCommercialUse(),
                bpm = result.bpm,
                bpmLabel = if (result.bpm > 0) result.bpm.toString() else "—",
                duration = result.duration,
                // The Internet Archive reports no length at all; "0:00" would read as a
                // fact rather than as the gap it is.
                durationLabel = if (result.duration > 0) durationLabel(result.duration) else "—",
                popularityValue = result.popularity ?: 0,
                popularityLabel = popularityLabel(result),
                hasPopularity = result.popularity != null,
                externalLink = result.externalLink.toString(),
                // Six label colours, cycled, so a long list still reads as a stack of records.
                discClass = "disc-${index % 6 + 1}",
                position = index,
            )
        }

        private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

        /**
         * Services hand tags over as one string. Jamendo sends none at all, the others
         * join on a comma — with or without the space — so one split covers them.
         */
        private fun tagList(tags: String): List<String> =
            tags.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(7)

        private fun popularityLabel(result: SearchResult): String {
            val popularity = result.popularity ?: return "no popularity signal"

            return "%,d %s".format(Locale.ENGLISH, popularity, result.service.popularityMetric())
        }
    }
}

data class FacetView(
    val key: String,
    val label: String,
    val count: Int,
    val enabled: Boolean,
)

data class LicenceView(
    val key: String,
    val label: String,
)

/**
 * A two-handle filter range. [available] is false when no result carries the value at
 * all, in which case the control is not worth showing.
 */
data class RangeView(
    val floor: Int,
    val ceiling: Int,
    val floorLabel: String,
    val ceilingLabel: String,
    val valueLabel: String,
    val available: Boolean,
)

internal fun durationLabel(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, total % 3600 / 60, total % 60)
    } else {
        "%d:%02d".format(total / 60, total % 60)
    }
}

internal fun SearchService.label(): String = when (this) {
    SearchService.JAMENDO -> "Jamendo"
    SearchService.CCMIXTER -> "ccMixter"
    SearchService.ICONS8 -> "Icons8"
    SearchService.INTERNETARCHIVE -> "Internet Archive"
    SearchService.FREESOUND -> "Freesound"
}

/** What the service's popularity counter actually counts. */
internal fun SearchService.popularityMetric(): String = when (this) {
    SearchService.JAMENDO -> "plays"
    SearchService.CCMIXTER -> "ratings"
    SearchService.ICONS8 -> "plays"
    SearchService.INTERNETARCHIVE -> "downloads"
    SearchService.FREESOUND -> "downloads"
}

internal fun CCLicense.label(): String = when (this) {
    CCLicense.CC0 -> "CC0"
    CCLicense.CC_BY -> "CC BY"
    CCLicense.CC_BY_SA -> "CC BY-SA"
    CCLicense.CC_BY_ND -> "CC BY-ND"
    CCLicense.CC_BY_NC -> "CC BY-NC"
    CCLicense.CC_BY_NC_SA -> "CC BY-NC-SA"
    CCLicense.CC_BY_NC_ND -> "CC BY-NC-ND"
    CCLicense.PUBLIC_DOMAIN -> "Public domain"
    CCLicense.UNKNOWN -> "Licence unknown"
}

/**
 * Drives the "Commercial use OK" filter and the licence chip's colour. The NC licences
 * rule it out; an unrecognised licence is not a promise either way, so it is not
 * presented as one.
 */
internal fun CCLicense.allowsCommercialUse(): Boolean = when (this) {
    CCLicense.CC0,
    CCLicense.CC_BY,
    CCLicense.CC_BY_SA,
    CCLicense.CC_BY_ND,
    CCLicense.PUBLIC_DOMAIN -> true

    CCLicense.CC_BY_NC,
    CCLicense.CC_BY_NC_SA,
    CCLicense.CC_BY_NC_ND,
    CCLicense.UNKNOWN -> false
}
