package com.poisonedyouth.application

open class GeneralException(val errorCode: ErrorCode, override val message: String) : RuntimeException(message)

class ApplicationServiceException(errorCode: ErrorCode, message: String) :
    GeneralException(errorCode, message)