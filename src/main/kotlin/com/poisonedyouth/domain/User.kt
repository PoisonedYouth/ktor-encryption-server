package com.poisonedyouth.domain

import com.poisonedyouth.security.PasswordEncryptionResult
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class User(
    val username: String,
    val encryptionResult: PasswordEncryptionResult,
    val lastUpdated: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
    val created: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
)
