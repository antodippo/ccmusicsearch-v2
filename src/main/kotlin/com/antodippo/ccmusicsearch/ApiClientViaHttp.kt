package com.antodippo.ccmusicsearch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service
@Primary
class ApiClientViaHttp : APIClient {
    override suspend fun get(uri: URI): HttpResponse<String> {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2000))
            .build()
        val request = HttpRequest.newBuilder()
            .uri(uri)
            .build();

        return withContext(Dispatchers.IO) {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }
}