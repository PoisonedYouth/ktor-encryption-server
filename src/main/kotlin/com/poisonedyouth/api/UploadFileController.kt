package com.poisonedyouth.api

import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ApiResult.Success
import com.poisonedyouth.application.ErrorCode.DESERIALIZATION_ERROR
import com.poisonedyouth.application.FileHandler
import com.poisonedyouth.application.UploadFileHistoryService
import com.poisonedyouth.application.deleteDirectoryRecursively
import com.poisonedyouth.plugins.ENCRYPTED_FILENAME_QUERY_PARAM
import com.poisonedyouth.plugins.PASSWORD_QUERY_PARAM
import com.poisonedyouth.plugins.handleFailureResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import kotlin.io.path.name
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface UploadFileController {

    suspend fun uploadFile(call: ApplicationCall, username: String)

    suspend fun getUploadFiles(call: ApplicationCall, username: String)

    suspend fun getUploadFileHistory(call: ApplicationCall, username: String)

    suspend fun deleteUploadFile(call: ApplicationCall, username: String)

    suspend fun downloadFile(call: ApplicationCall)
}

class UploadFileControllerImpl(
    private val fileHandler: FileHandler,
    private val uploadFileHistoryService: UploadFileHistoryService
) : UploadFileController {
    private val logger: Logger = LoggerFactory.getLogger(UploadFileController::class.java)

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun uploadFile(call: ApplicationCall, username: String) {
        try {
            val result = fileHandler.upload(
                username = username,
                origin = call.request.origin,
                contentLength = call.request.header(HttpHeaders.ContentLength)?.toLong(),
                multiPartData = call.receiveMultipart()
            )
            when (result) {
                is Success -> call.respond(HttpStatusCode.Created, result)
                is Failure -> handleFailureResponse(call, result)
            }
        } catch (e: Exception) {
            logger.error("Failed to read multipart body.", e)
            handleFailureResponse(call, Failure(DESERIALIZATION_ERROR, "Failed to read multipart body."))
        }
    }

    override suspend fun getUploadFiles(call: ApplicationCall, username: String) {
        when (val result = fileHandler.getUploadFiles(username)) {
            is Success -> call.respond(HttpStatusCode.OK, result)
            is Failure -> handleFailureResponse(call, result)
        }
    }

    override suspend fun getUploadFileHistory(call: ApplicationCall, username: String) {
        when (val result = uploadFileHistoryService.getUploadFileHistory(username)) {
            is Success -> call.respond(HttpStatusCode.OK, result)
            is Failure -> handleFailureResponse(call, result)
        }
    }

    override suspend fun deleteUploadFile(call: ApplicationCall, username: String) {
        when (val result =
            fileHandler.delete(username, call.request.queryParameters[ENCRYPTED_FILENAME_QUERY_PARAM])) {
            is Success -> call.respond(HttpStatusCode.OK, result)
            is Failure -> handleFailureResponse(call, result)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun downloadFile(call: ApplicationCall) {
        val queryParameters = call.request.queryParameters
        val password = queryParameters[PASSWORD_QUERY_PARAM]
        val encryptedFilename = queryParameters[ENCRYPTED_FILENAME_QUERY_PARAM]
        try {
            val result =
                if (encryptedFilename != null && password != null) {
                    fileHandler.download(
                        DownloadFileDto(
                            filename = encryptedFilename,
                            password = password
                        ),
                        ipAddress = call.request.origin.remoteHost
                    )
                } else {
                    fileHandler.download(
                        downloadFileDto = call.receive(),
                        ipAddress = call.request.origin.host
                    )
                }
            when (result) {
                is Success -> call.respondFile(
                    baseDir = result.value.parent.toFile(),
                    fileName = result.value.fileName.name
                )
                    .also { deleteDirectoryRecursively(result.value.parent) }

                is Failure -> handleFailureResponse(call, result)
            }
        } catch (e: Exception) {
            logger.error("Failed to transform body.", e)
            handleFailureResponse(call, Failure(DESERIALIZATION_ERROR, "Failed to transform body."))
        }
    }
}