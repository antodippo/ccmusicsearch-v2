package com.antodippo.ccmusicsearch

/**
 * Merges the per-service result lists into one ranking.
 *
 * Every service hands back a list already ordered by its own notion of relevance, but
 * those scores live on incomparable scales — a Jamendo relevance score means nothing
 * next to a Solr score from the Internet Archive. So we fuse on position alone
 * (Reciprocal Rank Fusion): each result contributes 1 / (K + rank), no normalisation
 * required.
 *
 * Each service supplies two orderings — the relevance order it returned, and the same
 * results re-sorted by popularity. Services that expose no popularity signal reuse
 * their relevance order for both, which keeps them on the same scale as everyone else
 * rather than quietly penalising them.
 *
 * The catalogues barely overlap today, so in practice this behaves as a weighted
 * interleave rather than true consensus fusion. It turns into the latter for free as
 * soon as two services index the same track.
 */
object RelevanceRanker {

    private const val K = 60.0
    private const val RELEVANCE_WEIGHT = 1.0
    private const val POPULARITY_WEIGHT = 0.5

    // Freesound is a sample library. Even filtered to a minimum duration it contributes
    // loops and one-shots, so it gets to compete for fewer of the top slots.
    private val serviceWeights = mapOf(SearchService.FREESOUND to 0.5)

    fun rank(resultsByService: List<Collection<SearchResult>>): List<SearchResult> =
        resultsByService
            .flatMap { scoreOneService(it.toList()) }
            .sortedByDescending { (_, score) -> score }
            .map { (result, _) -> result }

    private fun scoreOneService(results: List<SearchResult>): List<Pair<SearchResult, Double>> {
        if (results.isEmpty()) {
            return emptyList()
        }

        val weight = serviceWeights[results.first().service] ?: 1.0
        val popularityRanks = popularityRanks(results)

        return results.mapIndexed { index, result ->
            val relevanceRank = index + 1
            val popularityRank = popularityRanks?.get(index) ?: relevanceRank

            result to weight * (
                RELEVANCE_WEIGHT / (K + relevanceRank) + POPULARITY_WEIGHT / (K + popularityRank)
            )
        }
    }

    /**
     * Position of each result once sorted by popularity, indexed the same way as the
     * input. Null when the service exposes no popularity signal at all.
     */
    private fun popularityRanks(results: List<SearchResult>): IntArray? {
        if (results.none { it.popularity != null }) {
            return null
        }

        val ranks = IntArray(results.size)
        results.indices
            .sortedByDescending { results[it].popularity ?: -1 }
            .forEachIndexed { rank, index -> ranks[index] = rank + 1 }

        return ranks
    }
}
