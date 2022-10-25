package com.poisonedyouth.configuration

import io.ktor.server.application.Application
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ApplicationConfiguration {
    lateinit var databaseConfig: DatabaseConfig
    lateinit var securityConfig: SecurityConfig
    lateinit var uploadSettings: UploadSettings
    lateinit var passwordSettings: PasswordSettings

    fun getUploadDirectory(): Path = Paths.get(uploadSettings.directoryPath)

    fun getDefaultExpirationDays(): Long = uploadSettings.expirationDays

    fun getUploadMaxSizeInMb(): Long = uploadSettings.uploadMaxSizeInMb
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
    val fileIntegrityCheckHashingAlgorithm =
        securityObject.property("fileIntegrityCheckHashingAlgorithm").getString()
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

    // Upload Settings
    val uploadSettings = environment.config.config("ktor.uploadSettings")
    val directoryPath = uploadSettings.property("directoryPath").getString()
    val expirationDays = uploadSettings.property("expirationDays").getString().toLong()
    val uploadMaxSizeInMb = uploadSettings.property("uploadMaxSizeInMb").getString().toLong()
    ApplicationConfiguration.uploadSettings = UploadSettings(
        directoryPath = directoryPath,
        expirationDays = expirationDays,
        uploadMaxSizeInMb = uploadMaxSizeInMb
    )
    createUploadDirectory(directoryPath)

    // Password Settings
    val passwordSettings = environment.config.config("ktor.passwordSettings")
    val minimumLength = passwordSettings.property("minimumLength").getString().toInt()
    val mustContainUpperCase = passwordSettings.property("mustContainUpperCase").getString().toBoolean()
    val mustContainLowerCase = passwordSettings.property("mustContainLowerCase").getString().toBoolean()
    val mustContainDigits = passwordSettings.property("mustContainDigits").getString().toBoolean()
    val mustContainSpecial = passwordSettings.property("mustContainSpecial").getString().toBoolean()
    ApplicationConfiguration.passwordSettings = PasswordSettings(
        minimumLength = minimumLength,
        mustContainDigits = mustContainDigits,
        mustContainUpperCase = mustContainUpperCase,
        mustContainLowerCase = mustContainLowerCase,
        mustContainSpecial = mustContainSpecial
    )
}

private fun createUploadDirectory(directoryPath: String) {
    val path = Paths.get(directoryPath)
    if (Files.notExists(path)) {
        Files.createDirectory(path)
    }
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

data class UploadSettings(
    val directoryPath: String,
    val expirationDays: Long,
    val uploadMaxSizeInMb: Long,
)

data class PasswordSettings(
    val minimumLength: Int,
    val mustContainDigits: Boolean,
    val mustContainUpperCase: Boolean,
    val mustContainLowerCase: Boolean,
    val mustContainSpecial: Boolean
)
