package com.poisonedyouth.api

data class UserDto(
    val username: String,
    val password: String
)

data class UpdatePasswordDto(
    val newPassword: String
)