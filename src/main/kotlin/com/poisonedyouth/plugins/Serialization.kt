package com.poisonedyouth.plugins

import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson()

    }
    routing {
    }
}
