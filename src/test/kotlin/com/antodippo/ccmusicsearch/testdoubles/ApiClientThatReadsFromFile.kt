package com.antodippo.ccmusicsearch.testdoubles

import com.antodippo.ccmusicsearch.APIClient
import java.io.File
import java.net.URI
import java.net.http.HttpResponse

class ApiClientThatReadsFromFile(private val filename: String) : APIClient {
    override suspend fun get(uri: URI): HttpResponse<String> {
        return HttpDummyResponse(
            200,
            File("src/test/kotlin/com/antodippo/ccmusicsearch/apiresponses/${filename}.json").readText()
        )
    }
}
