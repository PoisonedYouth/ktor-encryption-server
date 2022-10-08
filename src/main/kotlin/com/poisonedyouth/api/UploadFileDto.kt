package com.poisonedyouth.api

data class UploadFileDto(
    val filename: String,
    val encryptedFilename: String,
    val password: String
)