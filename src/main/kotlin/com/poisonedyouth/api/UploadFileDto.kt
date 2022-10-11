package com.poisonedyouth.api

import java.time.LocalDateTime

data class UploadFileDto(
    val filename: String,
    val encryptedFilename: String,
    val password: String
)

data class UploadFileOverviewDto(
    val filename: String,
    val encryptedFilename: String,
    val hashSumBase64: String,
    val created: LocalDateTime
)