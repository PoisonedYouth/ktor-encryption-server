package com.poisonedyouth.application

import com.poisonedyouth.api.UserDto
import com.poisonedyouth.application.ErrorCode.AUTHENTICATION_FAILURE
import com.poisonedyouth.application.ErrorCode.PERSISTENCE_FAILURE
import com.poisonedyouth.application.ErrorCode.USER_NOT_FOUND
import com.poisonedyouth.domain.User
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.security.EncryptionManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface UserService {
    fun authenticate(userDto: UserDto): ApiResult<Boolean>
    fun save(userDto: UserDto): ApiResult<String>
}

class UserServiceImpl(
    private val userRepository: UserRepository
) : UserService {
    private val logger: Logger = LoggerFactory.getLogger(UserService::class.java)

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun save(userDto: UserDto): ApiResult<String> {
        return try {
            val encryptionResult = EncryptionManager.encryptPassword(userDto.password)

            ApiResult.Success(
                userRepository.save(
                    User(
                        username = userDto.username,
                        encryptionResult = encryptionResult,
                    )
                ).username
            )
        } catch (e: Exception) {
            logger.error("Failed to persist user '$userDto'.", e)
            ApiResult.Failure(PERSISTENCE_FAILURE, "Failed to persist user '$userDto'.")
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun authenticate(userDto: UserDto): ApiResult<Boolean> {
        val existingUser = userRepository.findByUsername(userDto.username)
        if (existingUser == null) {
            logger.error("User with username '${userDto.username}' does not exist.")
            return ApiResult.Failure(USER_NOT_FOUND, "User with username '${userDto.username}' does not exist.")
        }
        try {
            val decryptedPassword = EncryptionManager.decryptString(existingUser.encryptionResult, userDto.password)
            return ApiResult.Success(
                if (decryptedPassword != userDto.password) {
                    logger.error("Password for user with username '${userDto.username}' is wrong.")
                    false
                } else {
                    true
                }
            )
        } catch (e: Exception) {
            logger.error("Authentication of user with username '${userDto.username}' failed.", e)
            return ApiResult.Failure(
                AUTHENTICATION_FAILURE,
                "Authentication of user with username '${userDto.username}' failed."
            )
        }
    }
}