package com.antodippo.ccmusicsearch.infra

import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class PrintDuration {

    private val startTimes = mutableMapOf<String, Long>()
    private val logger = KotlinLogging.logger {}

    fun startMeasuringDuration(name: String) {
        this.startTimes[name] = System.currentTimeMillis()
        logger.info { "$name started at ${this.startTimes[name]}" }
    }
    fun finishMeasuringDuration(name: String) {
        val finish = System.currentTimeMillis()
        logger.info {  "$name finished at $finish, lasted for ${finish - startTimes[name]!!}ms" }
    }

}