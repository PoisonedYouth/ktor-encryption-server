import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCli::class)
suspend fun main(args: Array<String>) {
    val client = createHttpClient()
    val parser = ArgParser("ktor-encryption-server")

    class NewUser : Subcommand("newUser", "Create New User") {
        val username by parser.option(ArgType.String, shortName = "n", description = "New User").required()
        val password by parser.option(ArgType.String, shortName = "p", description = "User Password").required()

        override fun execute() {
            runBlocking {
                createNewUser(client, username, password)
            }
        }
    }

    parser.subcommands(NewUser())
    parser.parse(args)
}

private fun createHttpClient() = HttpClient(CIO) {
    install(ContentNegotiation) {
        jackson()
    }
}