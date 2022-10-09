package com.poisonedyouth.security

class IntegrityFailedException(
    override val message: String,
) : RuntimeException(message)