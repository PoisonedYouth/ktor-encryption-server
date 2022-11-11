package com.poisonedyouth.domain

import com.poisonedyouth.configuration.ApplicationConfiguration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit


data class UserSettings(
    val uploadFileExpirationDays: Long = ApplicationConfiguration.getDefaultExpirationDays(),
    val lastUpdated: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
    val created: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
) {
    init {
        require(uploadFileExpirationDays > 0) {
            "Upload file expiration days must be greater than 0."
        }
    }
}