package com.poisonedyouth.domain

import com.poisonedyouth.security.FileEncryptionResult
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class UploadFile(
    val filename: String,
    val encryptedFilename: String,
    val encryptionResult: FileEncryptionResult,
    val created: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
    val owner: User? = null,
)
