package com.poisonedyouth.api

import com.poisonedyouth.expiration.DEFAULT_EXPIRATION_DAYS

data class UserSettingsDto(
    val uploadFileExpirationDays: Long = DEFAULT_EXPIRATION_DAYS
)
