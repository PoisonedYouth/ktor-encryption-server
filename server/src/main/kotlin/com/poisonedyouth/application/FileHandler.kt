package com.poisonedyouth.application

import com.poisonedyouth.api.DownloadFileDto
import com.poisonedyouth.api.UploadAction.DOWNLOAD
import com.poisonedyouth.api.UploadAction.UPLOAD
import com.poisonedyouth.api.UploadFileDto
import com.poisonedyouth.api.UploadFileOverviewDto
import com.poisonedyouth.application.ApiResult.*
import com.poisonedyouth.application.ErrorCode.FAILED_TO_DELETE_FILE
import com.poisonedyouth.application.ErrorCode.FILE_NOT_FOUND
import com.poisonedyouth.application.ErrorCode.MISSING_HEADER
import com.poisonedyouth.application.ErrorCode.MISSING_PARAMETER
import com.poisonedyouth.application.ErrorCode.UPLOAD_SIZE_LIMIT_EXCEEDED
import com.poisonedyouth.application.ErrorCode.USER_NOT_FOUND
import com.poisonedyouth.configuration.ApplicationConfiguration
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
import java.io.OutputStream

interface FileHandler {
    suspend fun getUploadFiles(username: String): ApiResult<List<UploadFileOverviewDto>>
    suspend fun upload(
        username: String,
        origin: RequestConnectionPoint,
        contentLength: Long?,
        multiPartData: MultiPartData
    ): ApiResult<List<UploadFileDto>>

    suspend fun download(downloadFileDto: DownloadFileDto, ipAddress: String, outputStream: OutputStream): ApiResult<Unit>
    suspend fun delete(username: String, encryptedFilename: String?): ApiResult<String>

    suspend fun getContentType(downloadFileDto: DownloadFileDto): ApiResult<String>
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
        contentLength: Long?,
        multiPartData: MultiPartData
    ): ApiResult<List<UploadFileDto>> {
        logger.info("Starting upload files for user '$username'...")
        return try {
            if (contentLength == null) {
                return Failure(
                    MISSING_HEADER,
                    "Required 'ContentLength' header is missing."
                )
            }
            val contentLengthInMB = contentLength / 1000 / 1000
            if (contentLengthInMB > ApplicationConfiguration.getUploadMaxSizeInMb()) {
                logger.error("Upload ('$contentLengthInMB MB') exceeds configured upload limit of ('${ApplicationConfiguration.getUploadMaxSizeInMb()} MB')")
                return Failure(
                    UPLOAD_SIZE_LIMIT_EXCEEDED,
                    "Upload ('$contentLengthInMB MB') exceeds configured upload limit of ('${ApplicationConfiguration.getUploadMaxSizeInMb()} MB')"
                )
            }

            val existingUser = userRepository.findBy(username)
            if (existingUser == null) {
                logger.error("User with username '$username' does not exist.")
                throw ApplicationServiceException(USER_NOT_FOUND, "User with username '$username' does not exist.")
            }

            val result = fileEncryptionService.encryptFiles(multiPartData)

            Success(result.onEach {
                handleUploadFile(it, existingUser, origin)
            }.map {
                val uploadFileDto = UploadFileDto(
                    filename = it.second.filename,
                    encryptedFilename = it.second.encryptedFilename,
                    password = it.first,
                    downloadLink = buildDownloadLink(origin, it.second.encryptedFilename, it.first),
                    deleteLink = buildDeleteLink(origin, it.second.encryptedFilename)
                )
                logger.info("Successfully uploaded '${uploadFileDto.encryptedFilename}'.")
                uploadFileDto
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
    override suspend fun download(downloadFileDto: DownloadFileDto, ipAddress: String, outputStream: OutputStream): ApiResult<Unit> {
        logger.info("Starting download '${downloadFileDto.filename}...")
        return try {
            fileEncryptionService.decryptFile(downloadFileDto, outputStream).also {
                uploadFileHistoryService.addUploadFileHistoryEntry(
                    ipAddress = ipAddress,
                    action = DOWNLOAD,
                    encryptedFilename = downloadFileDto.filename
                )
            }
            logger.info("Successfully downloaded file '${downloadFileDto.filename}'.")
            Success(Unit)
        } catch (e: IllegalStateException) {
            logger.error("Download file '${downloadFileDto.filename}' not found.", e)
            Failure(FILE_NOT_FOUND, "Download file '${downloadFileDto.filename}' not found.")
        } catch (e: GeneralException) {
            Failure(e.errorCode, e.message)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun getUploadFiles(username: String): ApiResult<List<UploadFileOverviewDto>> {
        logger.info("Start getting upload files for user '$username'...")
        return try {
            val uploadFiles = uploadFileRepository.findAllBy(username).map {
                it.toUploadFileOverviewDto()
            }
            logger.info("Successfully get upload files for user '$username'.")
            Success(uploadFiles)
        } catch (e: GeneralException) {
            Failure(e.errorCode, e.message)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun delete(username: String, encryptedFilename: String?): ApiResult<String> {
        logger.info("Start deleting upload file '$encryptedFilename'for user '$username'...")
        return try {
            if (encryptedFilename == null) {
                throw ApplicationServiceException(MISSING_PARAMETER, "Required parameter 'encryptedfilename' missing.")
            }
            logger.info("Deleted upload file  with encrypted filename '$encryptedFilename' for user '$username'.")
            if (!uploadFileRepository.deleteBy(username, encryptedFilename)) {
                throw ApplicationServiceException(FAILED_TO_DELETE_FILE, "The upload file '$encryptedFilename' could not be deleted.")
            }
            return Success("Successfully deleted upload file '$encryptedFilename'")
        } catch (e: GeneralException) {
            Failure(e.errorCode, e.message)
        }
    }

    override suspend fun getContentType(downloadFileDto: DownloadFileDto): ApiResult<String> {
        val uploadFile = uploadFileRepository.findBy(downloadFileDto.filename)
        return if (uploadFile == null) {
            logger.error("Upload file with encrypted name '${downloadFileDto.filename}' not found.")
            Failure(FILE_NOT_FOUND, "Upload file with encrypted name '${downloadFileDto.filename}' not found.")
        } else {
            Success(uploadFile.mimeType)
        }
    }
}