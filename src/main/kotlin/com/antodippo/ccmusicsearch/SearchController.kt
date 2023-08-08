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

        if (q != null) {
            searchModel["q"] = q
            searchModel["songs"] = this.searchEngine.search(q)
        } else {
            searchModel["q"] = ""
            searchModel["songs"] = emptyList<SearchResult>()
        }

        return "search"
    }
}