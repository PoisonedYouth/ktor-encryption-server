import com.poisonedyouth.api.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson

suspend fun main() {
    val client = HttpClient(CIO){
        install(ContentNegotiation) {
            jackson()
        }
    }
    val response: HttpResponse = client.post("http://0.0.0.0:8080/api/user") {
        setBody(
            UserDto(
                username = "poisonedyouth",
                password = "AB12345678!2345c"
            )
        )
        contentType(ContentType.Application.Json)
    }
    println(response)
}