package com.poisonedyouth.plugins

import com.poisonedyouth.api.UserController
import io.ktor.server.application.call
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Routing.configureUserRouting() {
    val userController by inject<UserController>()

    route("api/user") {
        post("") {
            userController.createNewUser(call)
        }
        authenticate("userAuthentication") {
            delete("") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    userController.deleteUser(call, username)
                }
            }
            put("/password") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    userController.updatePassword(call, username)
                }
            }
            put("/settings") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    userController.updateSettings(call, username)
                }
            }
        }
    }


}