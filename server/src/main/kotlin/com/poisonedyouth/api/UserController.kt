package com.poisonedyouth.api

import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ApiResult.Success
import com.poisonedyouth.application.ErrorCode.DESERIALIZATION_ERROR
import com.poisonedyouth.application.UserService
import com.poisonedyouth.plugins.handleFailureResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface UserController {

    suspend fun createNewUser(call: ApplicationCall)

    suspend fun deleteUser(call: ApplicationCall, username: String)

    suspend fun updatePassword(call: ApplicationCall, username: String)

    suspend fun updateSettings(call: ApplicationCall, username: String)
}

class UserControllerImpl(
    private val userService: UserService
) : UserController {
    private val logger: Logger = LoggerFactory.getLogger(UserController::class.java)

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun createNewUser(call: ApplicationCall) {
        try {
            val user = call.receive<UserDto>()
            when (val result = userService.save(user)) {
                is Success -> call.respond(HttpStatusCode.Created, result)
                is Failure -> handleFailureResponse(call, result)
            }
        } catch (e: Exception) {
            logger.error("Failed to transform body.", e)
            handleFailureResponse(call, Failure(DESERIALIZATION_ERROR, "Failed to transform body."))
        }
    }

    override suspend fun deleteUser(call: ApplicationCall, username: String) {
        when (val result = userService.delete(username)) {
            is Success -> call.respond(HttpStatusCode.OK, result)
            is Failure -> handleFailureResponse(call, result)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun updatePassword(call: ApplicationCall, username: String) {
        try {
            when (val result = userService.updatePassword(username, call.receive())) {
                is Success -> call.respond(HttpStatusCode.OK, result)
                is Failure -> handleFailureResponse(call, result)
            }
        } catch (e: Exception) {
            logger.error("Failed to transform body.", e)
            handleFailureResponse(call, Failure(DESERIALIZATION_ERROR, "Failed to transform body."))
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override suspend fun updateSettings(call: ApplicationCall, username: String) {
        try {
            when (val result = userService.updateSettings(username, call.receive())) {
                is Success -> call.respond(HttpStatusCode.OK, result)
                is Failure -> handleFailureResponse(call, result)
            }
        } catch (e: Exception) {
            logger.error("Failed to transform body.", e)
            handleFailureResponse(call, Failure(DESERIALIZATION_ERROR, "Failed to transform body."))
        }
    }
}