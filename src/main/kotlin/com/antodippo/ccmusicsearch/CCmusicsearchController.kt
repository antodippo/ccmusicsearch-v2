package com.antodippo.ccmusicsearch

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
class CCmusicsearchController {
    @GetMapping
    fun hello(): String {
        return "Ciao, sono CC Music Search! (v2)"
    }
}