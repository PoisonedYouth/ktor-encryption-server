package com.poisonedyouth.application

import com.poisonedyouth.api.DownloadFileDto
import com.poisonedyouth.api.UploadFileDto
import com.poisonedyouth.api.UploadFileOverviewDto
import com.poisonedyouth.application.ApiResult.*
import com.poisonedyouth.application.ErrorCode.FILE_NOT_FOUND
import com.poisonedyouth.application.ErrorCode.MISSING_PARAMETER
import com.poisonedyouth.application.ErrorCode.USER_NOT_FOUND
import com.poisonedyouth.domain.UploadAction.DOWNLOAD
import com.poisonedyouth.domain.UploadAction.UPLOAD
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.domain.User
import com.poisonedyouth.domain.toUploadFileOverviewDto
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.plugins.ENCRYPTED_FILENAME_QUERY_PARAM
import com.poisonedyouth.plugins.PASSWORD_QUERY_PARAM
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.content.MultiPartData
import io.ktor.http.parametersOf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

interface FileHandler {
    suspend fun getUploadFiles(username: String): ApiResult<List<UploadFileOverviewDto>>
    suspend fun upload(
        username: String,
        origin: RequestConnectionPoint,
        multiPartData: MultiPartData
    ): ApiResult<List<UploadFileDto>>

    suspend fun download(downloadFileDto: DownloadFileDto, ipAddress: String): ApiResult<File>
    suspend fun delete(username: String, encryptedFilename: String?): ApiResult<Boolean>
}

class FileHandlerImpl(
    private val uploadFileRepository: UploadFileRepository,
    private val fileEncryptionService: FileEncryptionService,
    private val userRepository: UserRepository,
    private val uploadFileHistoryService: UploadFileHistoryService,
) : FileHandler {
    private val logger: Logger = LoggerFactory.getLogger(FileHandler::class.java)

    override suspend fun upload(
        username: String,
        origin: RequestConnectionPoint,
        multiPartData: MultiPartData
    ): ApiResult<List<UploadFileDto>> {
        return try {
            val existingUser = userRepository.findByUsername(username)
            if (existingUser == null) {
                logger.error("User with username '$username' does not exist.")
                throw ApplicationServiceException(USER_NOT_FOUND, "User with username '$username' does not exist.")
            }

            val result = fileEncryptionService.encryptFiles(multiPartData)

            Success(result.onEach {
                handleUploadFile(it, existingUser, origin)
            }.map {
                UploadFileDto(
                    filename = it.second.filename,
                    encryptedFilename = it.second.encryptedFilename,
                    password = it.first,
                    downloadLink = buildDownloadLink(origin, it.second.encryptedFilename, it.first),
                    deleteLink = buildDeleteLink(origin, it.second.encryptedFilename)
                )
            })
        } catch (e: GeneralException) {
            Failure(e.errorCode, e.message)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    private fun handleUploadFile(
        it: Pair<String, UploadFile>,
        existingUser: User,
        origin: RequestConnectionPoint
    ) {
        val updatedUploadFile = it.second.copy(
            owner = existingUser
        )
        uploadFileRepository.save(updatedUploadFile)
        uploadFileHistoryService.addUploadFileHistoryEntry(
            ipAddress = origin.remoteHost,
            action = UPLOAD,
            encryptedFilename = it.second.encryptedFilename
        )
    }

    private fun buildDeleteLink(origin: RequestConnectionPoint, encryptedFilename: String): String {
        val builder = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = origin.host,
            port = origin.port,
            pathSegments = listOf("api", "upload"),
            parameters = parametersOf(ENCRYPTED_FILENAME_QUERY_PARAM to listOf(encryptedFilename))
        )
        return builder.buildString()
    }

    private fun buildDownloadLink(origin: RequestConnectionPoint, encryptedFilename: String, password: String): String {
        val builder = URLBuilder(
            protocol = URLProtocol.HTTP,
            host = origin.host,
            port = origin.port,
            pathSegments = listOf("api", "download"),
            parameters = parametersOf(
                ENCRYPTED_FILENAME_QUERY_PARAM to listOf(encryptedFilename),
                PASSWORD_QUERY_PARAM to listOf(password)
            )
        )
        return builder.buildString()
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun download(downloadFileDto: DownloadFileDto, ipAddress: String): ApiResult<File> {
        return try {
            Success(fileEncryptionService.decryptFile(downloadFileDto).also {
                uploadFileHistoryService.addUploadFileHistoryEntry(
                    ipAddress = ipAddress,
                    action = DOWNLOAD,
                    encryptedFilename = downloadFileDto.filename
                )
            })
        } catch (e: IllegalStateException) {
            logger.error("Download file '${downloadFileDto.filename}' not found.", e)
            Failure(FILE_NOT_FOUND, "Download file '${downloadFileDto.filename}' not found.")
        } catch (e: GeneralException) {
            Failure(e.errorCode, e.message)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun getUploadFiles(username: String): ApiResult<List<UploadFileOverviewDto>> {
        return try {
            Success(uploadFileRepository.findAllByUsername(username).map {
                it.toUploadFileOverviewDto()
            })
        } catch (e: GeneralException) {
            Failure(e.errorCode, e.message)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun delete(username: String, encryptedFilename: String?): ApiResult<Boolean> {
        return try {
            if (encryptedFilename == null) {
                throw ApplicationServiceException(MISSING_PARAMETER, "Required parameter 'encryptedfilename' missing.")
            }
            logger.info("Deleted upload file  with encrypted filename '$encryptedFilename'.")
            Success(uploadFileRepository.deleteBy(username, encryptedFilename))
        } catch (e: GeneralException) {
            Failure(e.errorCode, e.message)
        }
    }
}