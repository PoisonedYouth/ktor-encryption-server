package com.poisonedyouth.configuration

import io.ktor.server.application.Application

class ApplicationConfiguration {
    lateinit var databaseConfig: DatabaseConfig
}

fun Application.setupApplicationConfiguration(): ApplicationConfiguration {
    val appConfig = ApplicationConfiguration()

    // Database
    val databaseObject = environment.config.config("ktor.database")
    val driverClass = databaseObject.property("driverClass").getString()
    val url = databaseObject.property("url").getString()
    val user = databaseObject.property("user").getString()
    val password = databaseObject.property("password").getString()
    val maxPoolSize = databaseObject.property("maxPoolSize").getString().toInt()
    appConfig.databaseConfig = DatabaseConfig(driverClass, url, user, password, maxPoolSize)
    return appConfig
}

data class DatabaseConfig(
    val driverClass: String,
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int
)
