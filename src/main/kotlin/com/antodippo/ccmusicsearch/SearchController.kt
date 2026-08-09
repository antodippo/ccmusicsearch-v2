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

        val songs = if (q != null) this.searchEngine.search(q) else emptyList()
        searchModel["page"] = SearchPage.from(q, songs)

        return "search"
    }
}