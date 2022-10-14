package com.poisonedyouth.plugins

import com.poisonedyouth.api.UserDto
import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ApiResult.Success
import com.poisonedyouth.application.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Routing.configureUserRouting() {
    val userService by inject<UserService>()

    route("api/user") {
        post("") {
            val user = call.receive<UserDto>()
            call.respond(HttpStatusCode.Created, userService.save(user))
        }
        authenticate("userAuthentication") {
            delete("") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    when (val result = userService.delete(username)) {
                        is Success -> call.respond(HttpStatusCode.OK, result)
                        is Failure -> handleFailureResponse(call, result)
                    }
                }
            }
            put("/password") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    when (val result = userService.updatePassword(username, call.receive())) {
                        is Success -> call.respond(HttpStatusCode.OK, result)
                        is Failure -> handleFailureResponse(call, result)
                    }
                }
            }
            put("/settings") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    when (val result = userService.updateSettings(username, call.receive())) {
                        is Success -> call.respond(HttpStatusCode.OK, result)
                        is Failure -> handleFailureResponse(call, result)
                    }
                }
            }
        }
    }


}