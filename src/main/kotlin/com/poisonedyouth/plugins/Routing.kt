package com.poisonedyouth.plugins

import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ErrorCode.MISSING_PARAMETER
import com.poisonedyouth.application.ErrorCode.NOT_ACCEPTED_MIME_TYPE
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        configureUploadRouting()
        configureUserRouting()
    }
}

suspend fun handleFailureResponse(call: ApplicationCall, failure: Failure) {
    val httpStatusCode = when (failure.errorCode) {
        NOT_ACCEPTED_MIME_TYPE,
        MISSING_PARAMETER -> HttpStatusCode.BadRequest

        else -> Companion.InternalServerError
    }
    call.respond(status = httpStatusCode, message = failure.errorMessage)
}