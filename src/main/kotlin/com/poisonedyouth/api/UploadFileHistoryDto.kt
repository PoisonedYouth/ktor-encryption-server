package com.poisonedyouth.api

import com.poisonedyouth.domain.UploadAction
import java.time.LocalDateTime

data class UploadFileHistoryDto(
    val encryptedFilename: String,
    val filename: String,
    val ipAddress: String,
    val created: LocalDateTime,
    val action: UploadAction,
)
