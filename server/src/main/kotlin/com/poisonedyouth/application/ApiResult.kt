package com.poisonedyouth.application

sealed class ApiResult<out T>{
    data class Failure(val errorCode: ErrorCode, val errorMessage: String) : ApiResult<Nothing>()
    data class Success<T>(val value: T) : ApiResult<T>()
}

enum class ErrorCode{
    PERSISTENCE_FAILURE,
    USER_NOT_FOUND,
    AUTHENTICATION_FAILURE,
    ENCRYPTION_FAILURE,
    FILE_NOT_FOUND,
    USER_ALREADY_EXIST,
    INTEGRITY_CHECK_FAILED,
    MISSING_PARAMETER,
    MISSING_HEADER,
    PASSWORD_REQUIREMENTS_NOT_FULFILLED,
    INPUT_VALIDATION_FAILED,
    UPLOAD_SIZE_LIMIT_EXCEEDED,
    NOT_ACCEPTED_MIME_TYPE,
    DESERIALIZATION_ERROR,
    FAILED_TO_DELETE_FILE
}