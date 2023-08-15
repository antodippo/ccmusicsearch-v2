package com.antodippo.ccmusicsearch.testdoubles

import com.antodippo.ccmusicsearch.APIClient
import java.lang.Exception
import java.net.URI
import java.net.http.HttpResponse

class ApiClientThatThrows : APIClient {
    override suspend fun get(uri: URI): HttpResponse<String> {
        throw Exception("This is an exception thrown by the test double")
    }
}