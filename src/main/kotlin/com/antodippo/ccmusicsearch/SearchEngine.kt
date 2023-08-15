package com.antodippo.ccmusicsearch

import com.antodippo.ccmusicsearch.apiservices.APIService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service


@Service
class SearchEngine(
    private val searchServices: List<APIService> = SearchService.values().map { it.toService() },
) {

    suspend fun search(query: String): List<SearchResult> = coroutineScope {
        val results = mutableListOf<SearchResult>()
        searchServices.map {
            async {
                results.addAll(it.search(query))
            }
        }.awaitAll()

        return@coroutineScope results.sortedByDescending { it.date }
    }
}