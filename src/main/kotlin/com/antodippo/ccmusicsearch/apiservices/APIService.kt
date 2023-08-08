package com.antodippo.ccmusicsearch.apiservices

import com.antodippo.ccmusicsearch.SearchResult

interface APIService {
    suspend fun search(query: String): Collection<SearchResult>
}