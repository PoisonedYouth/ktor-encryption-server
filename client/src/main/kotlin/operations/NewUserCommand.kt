package operations

import client
import createNewUser
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.Subcommand
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking

class NewUserCommand(parser: ArgParser) : Subcommand("newUser", "Create New User") {
    private val username by parser.option(ArgType.String, shortName = "n", description = "New User").required()
    private val password by parser.option(ArgType.String, shortName = "p", description = "User Password").required()

    override fun execute() {
        runBlocking {
            createNewUser(client, username, password)
        }
    }
}