package com.antodippo.ccmusicsearch.testdoubles

import com.antodippo.ccmusicsearch.APIClient
import java.net.URI
import java.net.http.HttpResponse

/**
 * Answers 200 with nothing in it, the way ccMixter does when a query asks for more
 * results than it is willing to return.
 */
class ApiClientThatReturnsAnEmptyBody : APIClient {
    override suspend fun get(uri: URI): HttpResponse<String> {
        return HttpDummyResponse(200, "")
    }
}
