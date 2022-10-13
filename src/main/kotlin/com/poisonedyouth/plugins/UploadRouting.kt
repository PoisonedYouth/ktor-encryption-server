package com.poisonedyouth.plugins

import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ApiResult.Success
import com.poisonedyouth.application.FileHandler
import com.poisonedyouth.application.UploadFileHistoryService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Routing.configureUploadRouting() {
    val fileHandler by inject<FileHandler>()
    val uploadFileHistoryService by inject<UploadFileHistoryService>()


    authenticate("userAuthentication") {
        route("api/upload") {
            post("") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    val result = fileHandler.upload(
                        username = username,
                        origin = call.request.origin,
                        multiPartData = call.receiveMultipart()
                    )
                    when (result) {
                        is Success -> call.respond(HttpStatusCode.Created, result)
                        is Failure -> handleFailureResponse(call, result)
                    }
                }
            }
            get("") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    when (val result = fileHandler.getUploadFiles(username)) {
                        is Success -> call.respond(HttpStatusCode.OK, result)
                        is Failure -> handleFailureResponse(call, result)
                    }
                }
            }
            get("/history") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    when (val result = uploadFileHistoryService.getUploadFileHistory(username)) {
                        is Success -> call.respond(HttpStatusCode.OK, result)
                        is Failure -> handleFailureResponse(call, result)
                    }
                }
            }
            delete("") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    when (val result =
                        fileHandler.delete(username, call.request.queryParameters["encryptedfilename"])) {
                        is Success -> call.respond(HttpStatusCode.OK, result)
                        is Failure -> handleFailureResponse(call, result)
                    }
                }
            }
        }
    }
    get("/download") {
        val queryParameters = call.request.queryParameters
        val result =
            if (queryParameters.contains("password") && queryParameters.contains("encryptedFilename")) {
                fileHandler.download(
                    encryptedFilename = queryParameters["encryptedFilename"]!!,
                    password = queryParameters["password"]!!,
                    ipAddress = call.request.origin.remoteHost
                )
            } else {
                fileHandler.download(
                    downloadFileDto = call.receive(),
                    ipAddress = call.request.origin.host
                )
            }
        when (result) {
            is Success -> call.respondFile(baseDir = result.value.parentFile, fileName = result.value.name)
                .also { result.value.delete() }

            is Failure -> handleFailureResponse(call, result)
        }
    }
}
