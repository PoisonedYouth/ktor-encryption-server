package com.poisonedyouth.configuration

import io.ktor.server.application.Application

object ApplicationConfiguration {
    lateinit var databaseConfig: DatabaseConfig
    lateinit var securityConfig: SecurityConfig
}

fun Application.setupApplicationConfiguration() {

    // Database
    val databaseObject = environment.config.config("ktor.database")
    val driverClass = databaseObject.property("driverClass").getString()
    val url = databaseObject.property("url").getString()
    val user = databaseObject.property("user").getString()
    val password = databaseObject.property("password").getString()
    val maxPoolSize = databaseObject.property("maxPoolSize").getString().toInt()
    ApplicationConfiguration.databaseConfig = DatabaseConfig(
        driverClass = driverClass,
        url = url,
        user = user,
        password = password,
        maxPoolSize = maxPoolSize
    )

    // Security
    val securityObject = environment.config.config("ktor.security")
    val fileIntegrityCheckHashingAlgorithm = securityObject.property("fileIntegrityCheckHashingAlgorithm").getString()
    val defaultPasswordKeySize = securityObject.property("defaultPasswordKeySizeBytes").getString().toInt()
    val defaultNonceLength = securityObject.property("defaultNonceLengthBytes").getString().toInt()
    val defaultSaltLength = securityObject.property("defaultSaltLengthBytes").getString().toInt()
    val defaultIterationCount = securityObject.property("defaultIterationCount").getString().toInt()
    val defaultGcmParameterSpecLength = securityObject.property("defaultGcmParameterSpecLength").getString().toInt()
    ApplicationConfiguration.securityConfig = SecurityConfig(
        fileIntegrityCheckHashingAlgorithm = fileIntegrityCheckHashingAlgorithm,
        defaultPasswordKeySizeBytes = defaultPasswordKeySize,
        defaultNonceLengthBytes = defaultNonceLength,
        defaultSaltLengthBytes = defaultSaltLength,
        defaultIterationCount = defaultIterationCount,
        defaultGcmParameterSpecLength = defaultGcmParameterSpecLength
    )
}

data class DatabaseConfig(
    val driverClass: String,
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int
)

data class SecurityConfig(
    val fileIntegrityCheckHashingAlgorithm: String,
    val defaultPasswordKeySizeBytes: Int,
    val defaultNonceLengthBytes: Int,
    val defaultSaltLengthBytes: Int,
    val defaultIterationCount: Int,
    val defaultGcmParameterSpecLength: Int
)
