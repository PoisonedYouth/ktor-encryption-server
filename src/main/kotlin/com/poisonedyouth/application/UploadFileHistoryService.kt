package com.poisonedyouth.application

import com.poisonedyouth.api.UploadFileHistoryDto
import com.poisonedyouth.application.ErrorCode.FILE_NOT_FOUND
import com.poisonedyouth.application.ErrorCode.PERSISTENCE_FAILURE
import com.poisonedyouth.application.ErrorCode.USER_NOT_FOUND
import com.poisonedyouth.domain.UploadAction
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.domain.UploadFileHistory
import com.poisonedyouth.persistence.UploadFileHistoryRepository
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.persistence.UserRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface UploadFileHistoryService {
    fun addUploadFileHistoryEntry(ipAddress: String, action: UploadAction, encryptedFilename: String): ApiResult<Unit>
    fun addUploadFileHistoryEntry(ipAddress: String, action: UploadAction, uploadFile: UploadFile): ApiResult<Unit>

    fun getUploadFileHistory(username: String): ApiResult<List<UploadFileHistoryDto>>
}

class UploadFileHistoryServiceImpl(
    private val uploadFileHistoryRepository: UploadFileHistoryRepository,
    private val uploadFileRepository: UploadFileRepository,
    private val userRepository: UserRepository
) : UploadFileHistoryService {
    private val logger: Logger = LoggerFactory.getLogger(UserService::class.java)

    override fun addUploadFileHistoryEntry(
        ipAddress: String,
        action: UploadAction,
        uploadFile: UploadFile
    ): ApiResult<Unit> {
        try {
            uploadFileHistoryRepository.save(
                UploadFileHistory(
                    ipAddress = ipAddress,
                    action = action,
                    uploadFile = uploadFile
                )
            )
            return ApiResult.Success(Unit)
        } catch (e: Exception) {
            logger.error("Adding upload file history entry for '${uploadFile.encryptedFilename}' failed.", e)
            return ApiResult.Failure(
                PERSISTENCE_FAILURE,
                "Adding upload file history entry for for '${uploadFile.encryptedFilename}' failed."
            )
        }
    }

    override fun addUploadFileHistoryEntry(
        ipAddress: String,
        action: UploadAction,
        encryptedFilename: String
    ): ApiResult<Unit> {
        val uploadFile = uploadFileRepository.findBy(encryptedFilename)
            ?: return ApiResult.Failure(
                FILE_NOT_FOUND,
                "Upload file with encrypted filename '$encryptedFilename' not found."
            )
        return addUploadFileHistoryEntry(
            ipAddress = ipAddress,
            action = action,
            uploadFile = uploadFile
        )
    }

    override fun getUploadFileHistory(username: String): ApiResult<List<UploadFileHistoryDto>> {
        try {
            val existingUser = userRepository.findByUsername(username)
            if (existingUser == null) {
                logger.error("User with username '${username}' does not exist.")
                return ApiResult.Failure(USER_NOT_FOUND, "User with username '${username}' does not exist.")
            }
            return ApiResult.Success(
                uploadFileHistoryRepository.findAllBy(existingUser).map { it.toUploadFileHistoryDto() })
        } catch (e: Exception) {
            logger.error("Loading upload file history entry for '${username}' failed.", e)
            return ApiResult.Failure(
                PERSISTENCE_FAILURE,
                "Loading upload file history entry for '${username}' failed."
            )
        }
    }
}

fun UploadFileHistory.toUploadFileHistoryDto() = UploadFileHistoryDto(
    encryptedFilename = this.uploadFile.encryptedFilename,
    filename = this.uploadFile.filename,
    ipAddress = this.ipAddress,
    created = this.created,
    action = this.action
)