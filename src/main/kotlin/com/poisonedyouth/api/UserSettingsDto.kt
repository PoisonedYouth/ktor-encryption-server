package com.poisonedyouth.api

import com.poisonedyouth.configuration.ApplicationConfiguration


data class UserSettingsDto(
    val uploadFileExpirationDays: Long = ApplicationConfiguration.getDefaultExpirationDays()
)
