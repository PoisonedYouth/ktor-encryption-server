package com.poisonedyouth.domain

import com.poisonedyouth.api.UploadFileOverviewDto
import com.poisonedyouth.security.FileEncryptionResult
import io.ktor.util.encodeBase64
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class UploadFile(
    val filename: String,
    val encryptedFilename: String,
    val encryptionResult: FileEncryptionResult,
    val created: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
    val owner: User? = null,
    val settings: SecuritySettings
)

fun UploadFile.toUploadFileOverviewDto() = UploadFileOverviewDto(
    filename = this.filename,
    encryptedFilename = this.encryptedFilename,
    created = this.created,
    hashSumBase64 = this.encryptionResult.hashSum.encodeBase64()
)
