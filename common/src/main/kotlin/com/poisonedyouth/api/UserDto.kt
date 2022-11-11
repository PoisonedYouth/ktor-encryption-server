package com.poisonedyouth.api

data class UserDto(
    val username: String,
    val password: String,
    val userSettings: UserSettingsDto = UserSettingsDto()
)

data class UpdatePasswordDto(
    val newPassword: String
)