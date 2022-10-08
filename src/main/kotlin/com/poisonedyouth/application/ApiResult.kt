package com.poisonedyouth.application

sealed class ApiResult<out T>{
    internal data class Failure(val errorCode: ErrorCode, val errorMessage: String) : ApiResult<Nothing>()
    internal data class Success<T>(val value: T) : ApiResult<T>()
}

enum class ErrorCode{
    PERSISTENCE_FAILURE,
    USER_NOT_FOUND,
    AUTHENTICATION_FAILURE,
    ENCRYPTION_FAILURE,
    FILE_NOT_FOUND
}