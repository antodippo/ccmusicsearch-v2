package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.SearchResult
import com.antodippo.ccmusicsearch.SearchService

interface APIService {
    /** Which catalogue this is, so the page can list only the ones actually being searched. */
    val service: SearchService

    suspend fun search(query: String): Collection<SearchResult>
}
