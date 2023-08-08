package com.antodippo.ccmusicsearch

import io.github.cdimascio.dotenv.dotenv
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CcmusicsearchApplication

fun main(args: Array<String>) {
	dotenv().entries().map { System.setProperty(it.key, it.value) }

	runApplication<CcmusicsearchApplication>(*args)
}
