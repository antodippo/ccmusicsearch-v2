package com.antodippo.ccmusicsearch

import com.antodippo.ccmusicsearch.apiservices.APIService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import org.springframework.stereotype.Service


@Service
class SearchEngine(
    private val searchServices: List<APIService> = SearchService.values().map { it.toService() },
) {

    private val logger = KotlinLogging.logger {}

    suspend fun search(query: String): List<SearchResult> = coroutineScope {
        val resultsByService = searchServices
            .map { service -> async { searchOne(service, query) } }
            .awaitAll()

        return@coroutineScope RelevanceRanker.rank(resultsByService)
    }

    /**
     * A search is worth running for whichever services answer. Each of them already
     * catches its own transport and parsing failures; this is the backstop for anything
     * that gets past that, which under coroutineScope would otherwise cancel the other
     * services and fail the request outright.
     */
    private suspend fun searchOne(service: APIService, query: String): Collection<SearchResult> =
        try {
            service.search(query)
        } catch (e: CancellationException) {
            // Not a service failure — the scope is being torn down and must stay torn down.
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Search failed on ${service.javaClass.simpleName}, continuing without it" }
            emptyList()
        }
}
