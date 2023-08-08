package com.antodippo.ccmusicsearch

import java.net.URI
import java.net.http.HttpResponse

interface APIClient {
    suspend fun get(uri: URI): HttpResponse<String>
}