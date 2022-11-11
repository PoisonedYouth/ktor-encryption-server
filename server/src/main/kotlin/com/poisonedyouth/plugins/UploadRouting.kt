package com.poisonedyouth.plugins

import com.poisonedyouth.api.DownloadFileDto
import com.poisonedyouth.api.UploadFileController
import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ApiResult.Success
import com.poisonedyouth.application.deleteDirectoryRecursively
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.io.path.name
import org.koin.ktor.ext.inject

const val ENCRYPTED_FILENAME_QUERY_PARAM = "encryptedfilename"

const val PASSWORD_QUERY_PARAM = "password"

fun Routing.configureUploadRouting() {
    val uploadFileController by inject<UploadFileController>()

    authenticate("userAuthentication") {
        route("api/upload") {
            post("") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    uploadFileController.uploadFile(call, username)
                }
            }
            get("") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    uploadFileController.getUploadFiles(call, username)
                }
            }
            get("/history") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    uploadFileController.getUploadFileHistory(call, username)
                }
            }
            delete("") {
                call.principal<UserIdPrincipal>()?.name?.let { username ->
                    uploadFileController.deleteUploadFile(call, username)
                }
            }
        }
    }
    get("api/download") {
        uploadFileController.downloadFile(call)
    }
}