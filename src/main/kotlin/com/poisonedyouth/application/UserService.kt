package com.poisonedyouth.application

import com.poisonedyouth.api.UpdatePasswordDto
import com.poisonedyouth.api.UserDto
import com.poisonedyouth.application.ErrorCode.AUTHENTICATION_FAILURE
import com.poisonedyouth.application.ErrorCode.INTEGRITY_CHECK_FAILED
import com.poisonedyouth.application.ErrorCode.PASSWORD_REQUIREMENTS_NOT_FULFILLED
import com.poisonedyouth.application.ErrorCode.PERSISTENCE_FAILURE
import com.poisonedyouth.application.ErrorCode.USER_ALREADY_EXIST
import com.poisonedyouth.application.ErrorCode.USER_NOT_FOUND
import com.poisonedyouth.domain.User
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.security.EncryptionManager
import com.poisonedyouth.security.IntegrityFailedException
import com.poisonedyouth.security.PasswordManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface UserService {
    fun authenticate(userDto: UserDto): ApiResult<Unit>
    fun save(userDto: UserDto): ApiResult<String>
    fun delete(username: String): ApiResult<String>
    fun updatePassword(username: String, passwordDto: UpdatePasswordDto): ApiResult<String>
}

class UserServiceImpl(
    private val userRepository: UserRepository,
    private val uploadFileRepository: UploadFileRepository,
) : UserService {
    private val logger: Logger = LoggerFactory.getLogger(UserService::class.java)

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun save(userDto: UserDto): ApiResult<String> {
        try {
            if (userRepository.findByUsername(userDto.username) != null) {
                return ApiResult.Failure(USER_ALREADY_EXIST, "User with username '${userDto.username}' already exist.")
            }
            val validationResult = PasswordManager.validatePassword(userDto.password)
            if (validationResult.isNotEmpty()) {
                logger.error("Password '${userDto.password}' does not fulfill requirements.")
                return ApiResult.Failure(
                    PASSWORD_REQUIREMENTS_NOT_FULFILLED,
                    "Password '${userDto.password}' does not fulfill requirements: ($validationResult)"
                )
            }
            val encryptionResult = EncryptionManager.encryptPassword(userDto.password)

            return ApiResult.Success(
                userRepository.save(
                    User(
                        username = userDto.username,
                        encryptionResult = encryptionResult,
                    )
                ).username
            )
        } catch (e: Exception) {
            logger.error("Failed to persist user '$userDto'.", e)
            return ApiResult.Failure(PERSISTENCE_FAILURE, "Failed to persist user '$userDto'.")
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun authenticate(userDto: UserDto): ApiResult<Unit> {
        return try {
            val existingUser = userRepository.findByUsername(userDto.username)
            if (existingUser == null) {
                logger.error("User with username '${userDto.username}' does not exist.")
                return ApiResult.Failure(USER_NOT_FOUND, "User with username '${userDto.username}' does not exist.")
            } else {
                val decryptedPassword = EncryptionManager.decryptString(existingUser.encryptionResult, userDto.password)
                if (decryptedPassword != userDto.password) {
                    logger.error("Password for user with username '${userDto.username}' is wrong.")
                    ApiResult.Failure(
                        AUTHENTICATION_FAILURE,
                        "Password for user with username '${userDto.username}' is wrong."
                    )
                } else {
                    ApiResult.Success(Unit)
                }
            }
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
        return try {
            val existingUser = userRepository.findByUsername(username)
            if (existingUser == null) {
                logger.error("User with username '${username}' does not exist.")
                ApiResult.Failure(USER_NOT_FOUND, "User with username '${username}' does not exist.")
            } else {
                uploadFileRepository.deleteAllBy(username)
                userRepository.delete(username)
                ApiResult.Success("Successfully deleted user")
            }
        } catch (e: Exception) {
            logger.error("Failed to delete user with username '${username}'.", e)
            ApiResult.Failure(
                AUTHENTICATION_FAILURE,
                "Failed to delete user with username '${username}'."
            )
        }
    }

    override fun updatePassword(username: String, passwordDto: UpdatePasswordDto): ApiResult<String> {
        return try {
            val existingUser = userRepository.findByUsername(username)
            if (existingUser == null) {
                logger.error("User with username '${username}' does not exist.")
                ApiResult.Failure(USER_NOT_FOUND, "User with username '${username}' does not exist.")
            } else {
                val validationResult = PasswordManager.validatePassword(passwordDto.newPassword)
                if (validationResult.isNotEmpty()) {
                    logger.error("Password '${passwordDto.newPassword}' does not fulfill requirements.")
                    return ApiResult.Failure(
                        PASSWORD_REQUIREMENTS_NOT_FULFILLED,
                        "Password '${passwordDto.newPassword}' does not fulfill requirements: ($validationResult)"
                    )
                }
                val encryptionResult = EncryptionManager.encryptPassword(passwordDto.newPassword)
                userRepository.save(
                    existingUser.copy(
                        encryptionResult = encryptionResult
                    )
                )
                ApiResult.Success("Successfully updated password .")
            }
        } catch (e: Exception) {
            logger.error("Failed to delete user with username '${username}'.", e)
            ApiResult.Failure(
                AUTHENTICATION_FAILURE,
                "Failed to delete user with username '${username}'."
            )
        }
    }
}