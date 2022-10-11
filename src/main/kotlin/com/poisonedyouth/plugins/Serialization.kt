package com.poisonedyouth.plugins

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson{
            registerModule(JavaTimeModule())
        }
    }
    routing {
    }
}
