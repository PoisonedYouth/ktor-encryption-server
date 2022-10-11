package com.poisonedyouth.plugins

import com.poisonedyouth.api.UserDto
import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ApiResult.Success
import com.poisonedyouth.application.FileHandler
import com.poisonedyouth.application.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
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
                            username,
                            call.receiveMultipart()
                        )
                        when (result) {
                            is Success -> call.respond(HttpStatusCode.Created, result)
                            is Failure -> call.respond(Companion.InternalServerError, result.errorCode)
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
            }
            get("/download") {
                when (val result = fileHandler.download(call.receive())) {
                    is Success -> call.respondFile(baseDir = result.value.parentFile, fileName = result.value.name)
                        .also { result.value.delete() }
                    is Failure -> call.respond(Companion.InternalServerError, result.errorMessage)                }
            }
        }
    }
}
