package com.antodippo.ccmusicsearch.testdoubles

import com.antodippo.ccmusicsearch.APIClient
import java.io.File
import java.net.URI
import java.net.http.HttpResponse

/**
 * Answers with each fixture in turn, repeating the last once they run out, and keeps every
 * URI it was asked for — so a test can check what was sent, and how many times.
 */
class ApiClientThatRecords(private vararg val filenames: String) : APIClient {

    val requests = mutableListOf<URI>()

    override suspend fun get(uri: URI): HttpResponse<String> {
        requests.add(uri)
        val filename = filenames[minOf(requests.size, filenames.size) - 1]

        return HttpDummyResponse(
            200,
            File("src/test/kotlin/com/antodippo/ccmusicsearch/apiresponses/${filename}.json").readText()
        )
    }
}
