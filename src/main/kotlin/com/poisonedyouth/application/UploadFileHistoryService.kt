package com.poisonedyouth.application

import com.poisonedyouth.api.UploadFileHistoryDto
import com.poisonedyouth.application.ErrorCode.FILE_NOT_FOUND
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
        return try {
            uploadFileRepository.findBy(uploadFile.encryptedFilename) ?: return ApiResult.Failure(
                FILE_NOT_FOUND,
                "Upload file with encrypted filename '${uploadFile.encryptedFilename}' not found."
            )

            uploadFileHistoryRepository.save(
                UploadFileHistory(
                    ipAddress = ipAddress,
                    action = action,
                    uploadFile = uploadFile
                )
            )
            ApiResult.Success(Unit)
        } catch (e: GeneralException) {
            ApiResult.Failure(e.errorCode, e.message)
        }
    }

    override fun addUploadFileHistoryEntry(
        ipAddress: String,
        action: UploadAction,
        encryptedFilename: String
    ): ApiResult<Unit> {
        return try {
            val uploadFile = uploadFileRepository.findBy(encryptedFilename)
                ?: return ApiResult.Failure(
                    FILE_NOT_FOUND,
                    "Upload file with encrypted filename '$encryptedFilename' not found."
                )
            addUploadFileHistoryEntry(
                ipAddress = ipAddress,
                action = action,
                uploadFile = uploadFile
            )
        } catch (e: GeneralException) {
            ApiResult.Failure(e.errorCode, e.message)
        }
    }

    override fun getUploadFileHistory(username: String): ApiResult<List<UploadFileHistoryDto>> {
        return try {
            val existingUser = userRepository.findBy(username)
            if (existingUser == null) {
                logger.error("User with username '${username}' does not exist.")
                throw ApplicationServiceException(USER_NOT_FOUND, "User with username '${username}' does not exist.")
            }
            ApiResult.Success(
                uploadFileHistoryRepository.findAllBy(existingUser).map { it.toUploadFileHistoryDto() })
        } catch (e: GeneralException) {
            ApiResult.Failure(e.errorCode, e.message)
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