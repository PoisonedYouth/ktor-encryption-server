package com.poisonedyouth.security

import com.poisonedyouth.application.ErrorCode.INTEGRITY_CHECK_FAILED
import com.poisonedyouth.application.GeneralException

class IntegrityFailedException(
    override val message: String,
) : GeneralException(INTEGRITY_CHECK_FAILED, message)