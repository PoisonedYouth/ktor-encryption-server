package com.poisonedyouth.plugins

import com.poisonedyouth.api.UserDto
import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ApiResult.Success
import com.poisonedyouth.application.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authentication
import io.ktor.server.auth.basic
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger(Application::class.java)

fun Application.configureSecurity() {
    val userService by inject<UserService>()

    install(StatusPages) {
        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respond(
                HttpStatusCode.Unauthorized, "Not authorized to upload files."
            )
        }
    }

    authentication {
        basic(name = "userAuthentication") {
            realm = "ktor-encryption-server"
            validate { credentials ->
                val authenticationResult = userService.authenticate(
                    (UserDto(credentials.name, credentials.password))
                )
                when (authenticationResult) {
                    is Success -> {
                        UserIdPrincipal(credentials.name)
                    }

                    is Failure -> {
                        logger.error("Authentication failed for user '${credentials.name}'!")
                        null
                    }
                }
            }
        }
    }
}
