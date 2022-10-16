package com.poisonedyouth.persistence

import com.poisonedyouth.application.ErrorCode.PERSISTENCE_FAILURE
import com.poisonedyouth.application.GeneralException

class PersistenceException(message: String, cause: Throwable) :
    GeneralException(PERSISTENCE_FAILURE, message, cause)