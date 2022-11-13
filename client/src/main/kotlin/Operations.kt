import com.poisonedyouth.api.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson

const val BASE_URL = "http://0.0.0.0:8080/api"

val client = createHttpClient()
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
        println(result.bodyAsText())
    } else {
        println("Creation of new user failed because of '${result.bodyAsText()}' (${result.status})")
    }
}

private fun createHttpClient() = HttpClient(CIO) {
    install(ContentNegotiation) {
        jackson()
    }
}