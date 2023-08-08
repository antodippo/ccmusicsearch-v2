package com.antodippo.ccmusicsearch

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import javax.net.ssl.SSLSession

class ApiClientTestDouble(private val service: SearchService) : APIClient {
    override suspend fun get(uri: URI): HttpResponse<String> {
        return HttpResponseDummy(
            200,
            File("src/test/kotlin/com/antodippo/ccmusicsearch/apiresponses/${service.toString().lowercase()}.json").readText()
        )
    }
}

class HttpResponseDummy (
    private val statusCode: Int,
    private val body: String
) : HttpResponse<String> {

    override fun statusCode(): Int {
        return statusCode
    }

    override fun body(): String {
        return body
    }

    override fun request(): HttpRequest {
        throw UnsupportedOperationException()
    }

    override fun previousResponse(): Optional<HttpResponse<String>> {
        throw UnsupportedOperationException()
    }

    override fun headers(): HttpHeaders {
        throw UnsupportedOperationException()
    }

    override fun sslSession(): Optional<SSLSession> {
        throw UnsupportedOperationException()
    }

    override fun uri(): URI {
        throw UnsupportedOperationException()
    }

    override fun version(): HttpClient.Version {
        throw UnsupportedOperationException()
    }
}
