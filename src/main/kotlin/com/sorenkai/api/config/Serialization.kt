package com.sorenkai.api.config

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiationConfig
import io.ktor.server.application.install
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(json = defaultJson())
    }
}

private fun defaultJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    prettyPrint = false
    isLenient = false
}
