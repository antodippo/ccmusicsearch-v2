package com.antodippo.ccmusicsearch

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class SearchController(private val searchEngine: SearchEngine) {
    @GetMapping("/")
    suspend fun search(searchModel: Model, @RequestParam q: String?): String {

        // A submitted-but-empty box is a first visit, not a search for nothing. Normalising
        // here rather than at the call site keeps the two apart everywhere downstream: the
        // page's hasQuery is what picks the welcome view over an empty workspace.
        val query = q?.takeIf { it.isNotBlank() }

        val songs = if (query != null) this.searchEngine.search(query) else emptyList()
        searchModel["page"] = SearchPage.from(query, songs)

        return "search"
    }
}