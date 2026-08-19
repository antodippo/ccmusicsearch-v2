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

    // One client for the whole application rather than one per call. It is immutable and
    // thread-safe, and it owns the connection pool — rebuilding it every time threw that
    // pool away, which costs a fresh TCP and TLS handshake on every search against every
    // service.
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .build()

    override suspend fun get(uri: URI): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(uri)
            // Without this only *connecting* was bounded. SearchEngine awaits every service
            // before it can render, so one server that accepts the connection and then goes
            // quiet held the whole page open indefinitely. A service that times out is
            // caught upstream and costs us its results, which is the outcome we want.
            .timeout(REQUEST_TIMEOUT)
            .build()

        return withContext(Dispatchers.IO) {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(8)
    }
}
