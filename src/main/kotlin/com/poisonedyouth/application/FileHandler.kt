package com.poisonedyouth.application

import com.poisonedyouth.api.DownloadFileDto
import com.poisonedyouth.api.UploadFileDto
import com.poisonedyouth.application.ErrorCode.ENCRYPTION_FAILURE
import com.poisonedyouth.application.ErrorCode.FILE_NOT_FOUND
import com.poisonedyouth.application.ErrorCode.USER_NOT_FOUND
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.persistence.UserRepository
import io.ktor.http.content.MultiPartData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.OutputStream

interface FileHandler {

    suspend fun upload(username: String, multiPartData: MultiPartData): ApiResult<List<UploadFileDto>>
    suspend fun download(outputStream: OutputStream, downloadFileDto: DownloadFileDto): ApiResult<Unit>
}

class FileHandlerImpl(
    private val uploadFileRepository: UploadFileRepository,
    private val fileEncryptionService: FileEncryptionService,
    private val userRepository: UserRepository
) : FileHandler {
    private val logger: Logger = LoggerFactory.getLogger(UserService::class.java)

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun upload(username: String, multiPartData: MultiPartData): ApiResult<List<UploadFileDto>> {
        val existingUser = userRepository.findByUsername(username)
        if (existingUser == null) {
            logger.error("User with username '$username' does not exist.")
            ApiResult.Failure(USER_NOT_FOUND, "User with username '$username' does not exist.")
        }
        try {
            val result = fileEncryptionService.encryptFiles(multiPartData)

            return ApiResult.Success(result.onEach {
                val updatedUploadFile = it.second.copy(
                    owner = existingUser
                )
                uploadFileRepository.save(updatedUploadFile)
            }.map {
                UploadFileDto(
                    it.second.filename,
                    it.second.encryptedFilename,
                    it.first
                )
            })
        } catch (e: Exception) {
            logger.error("Upload of files failed.", e)
            return ApiResult.Failure(ENCRYPTION_FAILURE, "Upload of failes failed.")
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun download(outputStream: OutputStream, downloadFileDto: DownloadFileDto): ApiResult<Unit> {
        return try {
            fileEncryptionService.decryptFile(outputStream, downloadFileDto)
            ApiResult.Success(Unit)
        } catch (e: IllegalStateException) {
            logger.error("Download file '${downloadFileDto.filename}' not found.", e)
            ApiResult.Failure(FILE_NOT_FOUND, "Download file '${downloadFileDto.filename}' not found.")
        } catch (e: Exception) {
            logger.error("Failed to decrypt file '$downloadFileDto'.", e)
            ApiResult.Failure(ENCRYPTION_FAILURE, "")
        }
    }
}