package com.poisonedyouth.domain

import com.poisonedyouth.expiration.DEFAULT_EXPIRATION_DAYS
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit


data class UserSettings(
    val uploadFileExpirationDays: Long = DEFAULT_EXPIRATION_DAYS,
    val lastUpdated: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
    val created: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
)