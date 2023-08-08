package com.antodippo.ccmusicsearch

import com.antodippo.ccmusicsearch.apiservices.APIService
import com.antodippo.ccmusicsearch.infra.PrintDuration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service


@Service
class SearchEngine(
    private val searchServices: List<APIService> = SearchService.values().map { it.toService() },
    private val printDuration: PrintDuration
) {

    suspend fun search(query: String): List<SearchResult> = coroutineScope {
        printDuration.startMeasuringDuration("SearchEngine")
        val results = mutableListOf<SearchResult>()
        searchServices.map {
            async {
                results.addAll(it.search(query))
            }
        }.awaitAll()
        printDuration.finishMeasuringDuration("SearchEngine")

        return@coroutineScope results.sortedByDescending { it.date }
    }
}