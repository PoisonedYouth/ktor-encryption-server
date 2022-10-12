package com.poisonedyouth.application

import com.poisonedyouth.api.UserDto
import com.poisonedyouth.application.ErrorCode.AUTHENTICATION_FAILURE
import com.poisonedyouth.application.ErrorCode.INTEGRITY_CHECK_FAILED
import com.poisonedyouth.application.ErrorCode.PERSISTENCE_FAILURE
import com.poisonedyouth.application.ErrorCode.USER_ALREADY_EXIST
import com.poisonedyouth.application.ErrorCode.USER_NOT_FOUND
import com.poisonedyouth.domain.User
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.security.EncryptionManager
import com.poisonedyouth.security.IntegrityFailedException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface UserService {
    fun authenticate(userDto: UserDto): ApiResult<Boolean>
    fun save(userDto: UserDto): ApiResult<String>
    fun delete(username: String): ApiResult<String>
}

class UserServiceImpl(
    private val userRepository: UserRepository,
    private val uploadFileRepository: UploadFileRepository,
) : UserService {
    private val logger: Logger = LoggerFactory.getLogger(UserService::class.java)

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun save(userDto: UserDto): ApiResult<String> {
        if (userRepository.findByUsername(userDto.username) != null) {
            return ApiResult.Failure(USER_ALREADY_EXIST, "User with username '${userDto.username}' already exist.")
        }
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
        return try {
            val decryptedPassword = EncryptionManager.decryptString(existingUser.encryptionResult, userDto.password)
            ApiResult.Success(
                if (decryptedPassword != userDto.password) {
                    logger.error("Password for user with username '${userDto.username}' is wrong.")
                    false
                } else {
                    true
                }
            )
        } catch (e: IntegrityFailedException) {
            logger.error("Integrity check for user with username '${userDto.username}' failed.", e)
            ApiResult.Failure(
                INTEGRITY_CHECK_FAILED,
                "Integrity check for user with username '${userDto.username}' failed."
            )
        } catch (e: Exception) {
            logger.error("Authentication of user with username '${userDto.username}' failed.", e)
            ApiResult.Failure(
                AUTHENTICATION_FAILURE,
                "Authentication of user with username '${userDto.username}' failed."
            )
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun delete(username: String): ApiResult<String> {
        val existingUser = userRepository.findByUsername(username)
        if (existingUser == null) {
            logger.error("User with username '${username}' does not exist.")
            return ApiResult.Failure(USER_NOT_FOUND, "User with username '${username}' does not exist.")
        }
        return try {
            uploadFileRepository.deleteAllBy(username)
            userRepository.delete(username)
            ApiResult.Success("Successfully deleted user")
        } catch (e: Exception) {
            logger.error("Failed to delete user with username '${username}'.", e)
            ApiResult.Failure(
                AUTHENTICATION_FAILURE,
                "Failed to delete user with username '${username}'."
            )
        }
    }
}