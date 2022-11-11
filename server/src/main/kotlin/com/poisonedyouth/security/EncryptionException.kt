package com.poisonedyouth.security

import com.poisonedyouth.application.ErrorCode.ENCRYPTION_FAILURE
import com.poisonedyouth.application.GeneralException

class EncryptionException(override val message: String) :
    GeneralException(ENCRYPTION_FAILURE, message)