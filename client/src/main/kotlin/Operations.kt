import com.poisonedyouth.api.UpdatePasswordDto
import com.poisonedyouth.api.UserDto
import com.poisonedyouth.api.UserSettingsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson

const val BASE_URL = "http://0.0.0.0:8080/api"
suspend fun createNewUser(client: HttpClient, username: String, userPassword: String) {
    val result = client.post("$BASE_URL/user") {
        setBody(
            UserDto(
                username = username,
                password = userPassword
            )
        )
        contentType(ContentType.Application.Json)
    }
    if (result.status == HttpStatusCode.Created) {
        val success = result.body<Success<String>>()
        println("Successfully created user with username '${success.value}'")
    } else {
        println("Creation of new user failed because of '${result.bodyAsText()}' (${result.status})")
    }
}

suspend fun deleteExistingUser(client: HttpClient) {
    val result = client.delete("$BASE_URL/user")
    if (result.status == HttpStatusCode.OK) {
        val success = result.body<Success<String>>()
        println(success.value)
    } else {
        println("Deletion of user failed because of '${result.bodyAsText()}' (${result.status})")
    }
}

suspend fun updateUserPassword(client: HttpClient, newPassword: String?) {
    if (newPassword == null) {
        println("Missing parameter '-np'")
        return
    }
    val result = client.put("$BASE_URL/user/password") {
        setBody(
            UpdatePasswordDto(
                newPassword = newPassword
            )
        )
        contentType(ContentType.Application.Json)
    }
    if (result.status == HttpStatusCode.OK) {
        val success = result.body<Success<String>>()
        println(success.value)
    } else {
        println("Update of user password failed because of '${result.bodyAsText()}' (${result.status})")
    }
}

suspend fun updateUserSettings(client: HttpClient, uploadFileExpirationDays: Int?) {
    if (uploadFileExpirationDays == null) {
        println("Missing parameter '-ed'")
        return
    }
    val result = client.put("$BASE_URL/user/settings") {
        setBody(
            UserSettingsDto(
                uploadFileExpirationDays = uploadFileExpirationDays.toLong()
            )
        )
        contentType(ContentType.Application.Json)
    }
    if (result.status == HttpStatusCode.OK) {
        val success = result.body<Success<String>>()
        println(success.value)
    } else {
        println("Update of user settings failed because of '${result.bodyAsText()}' (${result.status})")
    }
}


fun createHttpClient() = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        jackson()
    }
}

fun createAuthenticatedHttpClient(username: String, password: String) = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        jackson()
    }
    install(Auth) {
        basic {
            credentials {
                BasicAuthCredentials(username = username, password = password)
            }
            realm = "ktor-encryption-server"
        }
    }
}

data class Success<T>(val value: T)