package com.poisonedyouth.domain

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class UploadFileHistory(
    val ipAddress: String,
    val created: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
    val action: UploadAction,
    val uploadFile: UploadFile
)

enum class UploadAction {
    UPLOAD,
    DOWNLOAD
}
