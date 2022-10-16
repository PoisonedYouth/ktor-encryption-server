package com.poisonedyouth.application

import com.poisonedyouth.api.UpdatePasswordDto
import com.poisonedyouth.api.UserDto
import com.poisonedyouth.api.UserSettingsDto
import com.poisonedyouth.application.ErrorCode.AUTHENTICATION_FAILURE
import com.poisonedyouth.application.ErrorCode.INPUT_VALIDATION_FAILED
import com.poisonedyouth.application.ErrorCode.PASSWORD_REQUIREMENTS_NOT_FULFILLED
import com.poisonedyouth.application.ErrorCode.PERSISTENCE_FAILURE
import com.poisonedyouth.application.ErrorCode.USER_ALREADY_EXIST
import com.poisonedyouth.application.ErrorCode.USER_NOT_FOUND
import com.poisonedyouth.domain.User
import com.poisonedyouth.domain.UserSettings
import com.poisonedyouth.domain.defaultSecurityFileSettings
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.security.EncryptionManager
import com.poisonedyouth.security.PasswordManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface UserService {
    fun authenticate(userDto: UserDto): ApiResult<Unit>
    fun save(userDto: UserDto): ApiResult<String>
    fun delete(username: String): ApiResult<String>
    fun updatePassword(username: String, passwordDto: UpdatePasswordDto): ApiResult<String>
    fun updateSettings(username: String, userSettingsDto: UserSettingsDto): ApiResult<String>
}

class UserServiceImpl(
    private val userRepository: UserRepository,
    private val uploadFileRepository: UploadFileRepository,
) : UserService {
    private val logger: Logger = LoggerFactory.getLogger(UserService::class.java)
    override fun save(userDto: UserDto): ApiResult<String> {
        try {
            if (userRepository.findByUsername(userDto.username) != null) {
                throw ApplicationServiceException(
                    USER_ALREADY_EXIST,
                    "User with username '${userDto.username}' already exist."
                )
            }
            val validationResult = PasswordManager.validatePassword(userDto.password)
            if (validationResult.isNotEmpty()) {
                logger.error("Password '${userDto.password}' does not fulfill requirements.")
                throw ApplicationServiceException(
                    PASSWORD_REQUIREMENTS_NOT_FULFILLED,
                    "Password '${userDto.password}' does not fulfill requirements: ($validationResult)"
                )
            }
            return ApiResult.Success(
                userRepository.save(
                    User(
                        username = userDto.username,
                        encryptionResult = EncryptionManager.encryptPassword(userDto.password),
                        userSettings = UserSettings(
                            uploadFileExpirationDays = userDto.userSettings.uploadFileExpirationDays
                        ),
                        securitySettings = defaultSecurityFileSettings()
                    )
                ).username
            )
        } catch (e: GeneralException) {
            return ApiResult.Failure(PERSISTENCE_FAILURE, e.message)
        }
    }

    override fun authenticate(userDto: UserDto): ApiResult<Unit> {
        return try {
            val existingUser = userRepository.findByUsername(userDto.username)
            if (existingUser == null) {
                logger.error("User with username '${userDto.username}' does not exist.")
                throw ApplicationServiceException(
                    USER_NOT_FOUND,
                    "User with username '${userDto.username}' does not exist."
                )
            }
            val decryptedPassword = EncryptionManager.decryptString(
                existingUser.encryptionResult,
                existingUser.securitySettings,
                userDto.password
            )
            if (decryptedPassword != userDto.password) {
                logger.error("Password for user with username '${userDto.username}' is wrong.")
                throw ApplicationServiceException(
                    AUTHENTICATION_FAILURE,
                    "Password for user with username '${userDto.username}' is wrong."
                )
            }
            ApiResult.Success(Unit)
        } catch (e: GeneralException) {
            ApiResult.Failure(e.errorCode, e.message)
        }
    }

    override fun delete(username: String): ApiResult<String> {
        return try {
            val existingUser = userRepository.findByUsername(username)
            if (existingUser == null) {
                logger.error("User with username '${username}' does not exist.")
                throw ApplicationServiceException(USER_NOT_FOUND, "User with username '${username}' does not exist.")
            }
            uploadFileRepository.deleteAllBy(username)
            userRepository.delete(username)
            ApiResult.Success("Successfully deleted user")
        } catch (e: GeneralException) {
            ApiResult.Failure(e.errorCode, e.message)
        }
    }

    override fun updatePassword(username: String, passwordDto: UpdatePasswordDto): ApiResult<String> {
        return try {
            val existingUser = userRepository.findByUsername(username)
            if (existingUser == null) {
                logger.error("User with username '${username}' does not exist.")
                throw ApplicationServiceException(USER_NOT_FOUND, "User with username '${username}' does not exist.")
            }
            val validationResult = PasswordManager.validatePassword(passwordDto.newPassword)
            if (validationResult.isNotEmpty()) {
                logger.error("Password '${passwordDto.newPassword}' does not fulfill requirements.")
                throw ApplicationServiceException(
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
        } catch (e: GeneralException) {
            ApiResult.Failure(e.errorCode, e.message)
        }
    }

    override fun updateSettings(username: String, userSettingsDto: UserSettingsDto): ApiResult<String> {
        return try {
            val existingUser = userRepository.findByUsername(username)
            if (existingUser == null) {
                logger.error("User with username '${username}' does not exist.")
                throw ApplicationServiceException(USER_NOT_FOUND, "User with username '${username}' does not exist.")
            }
            userRepository.save(
                existingUser.copy(
                    userSettings = UserSettings(
                        uploadFileExpirationDays = userSettingsDto.uploadFileExpirationDays
                    )
                )
            )
            ApiResult.Success("Successfully updated user settings.")
        } catch (e: IllegalArgumentException) {
            logger.error("User settings '$userSettingsDto' not fulfill requirements", e)
            ApiResult.Failure(INPUT_VALIDATION_FAILED, "User settings '$userSettingsDto' not fulfill requirements")
        } catch (e: GeneralException) {
            ApiResult.Failure(e.errorCode, e.message)
        }
    }
}