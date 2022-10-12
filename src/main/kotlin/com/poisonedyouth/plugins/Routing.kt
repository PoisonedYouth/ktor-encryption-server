package com.poisonedyouth.plugins

import com.poisonedyouth.api.UserDto
import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ApiResult.Success
import com.poisonedyouth.application.ErrorCode.MISSING_PARAMETER
import com.poisonedyouth.application.FileHandler
import com.poisonedyouth.application.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val userService by inject<UserService>()
    val fileHandler by inject<FileHandler>()

    routing {
        route("/api") {
            post("/user") {
                val user = call.receive<UserDto>()
                call.respond(HttpStatusCode.Created, userService.save(user))
            }
            authenticate("userAuthentication") {
                post("/upload") {
                    call.principal<UserIdPrincipal>()?.name?.let { username ->
                        val result = fileHandler.upload(
                            username = username,
                            ipAddress = call.request.origin.host,
                            multiPartData = call.receiveMultipart()
                        )
                        when (result) {
                            is Success -> call.respond(HttpStatusCode.Created, result)
                            is Failure -> handleFailureResponse(call, result)
                        }
                    }
                }
                get("/uploads") {
                    call.principal<UserIdPrincipal>()?.name?.let { username ->
                        when (val result = fileHandler.getUploadFiles(username)) {
                            is Success -> call.respond(HttpStatusCode.OK, result)
                            is Failure -> call.respond(Companion.InternalServerError, result.errorCode)
                        }
                    }
                }
                delete("/upload") {
                    call.principal<UserIdPrincipal>()?.name?.let { username ->
                        when (val result =
                            fileHandler.delete(username, call.request.queryParameters["encryptedfilename"])) {
                            is Success -> call.respond(HttpStatusCode.OK, result)
                            is Failure -> handleFailureResponse(call, result)
                        }
                    }
                }
                delete("/user") {
                    call.principal<UserIdPrincipal>()?.name?.let { username ->
                        when (val result = userService.delete(username)) {
                            is Success -> call.respond(HttpStatusCode.OK, result)
                            is Failure -> handleFailureResponse(call, result)
                        }
                    }
                }
            }
            get("/download") {
                when (val result = fileHandler.download(
                    downloadFileDto = call.receive(),
                    ipAddress = call.request.origin.host
                )) {
                    is Success -> call.respondFile(baseDir = result.value.parentFile, fileName = result.value.name)
                        .also { result.value.delete() }

                    is Failure -> handleFailureResponse(call, result)
                }
            }
        }
    }


}

suspend fun handleFailureResponse(call: ApplicationCall, failure: Failure) {
    val httpStatusCode = when (failure.errorCode) {
        MISSING_PARAMETER -> HttpStatusCode.BadRequest
        else -> Companion.InternalServerError
    }
    call.respond(status = httpStatusCode, message = failure.errorMessage)
}