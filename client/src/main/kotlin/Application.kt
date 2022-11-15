import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    val parser = ArgParser(
        programName = "ktor-encryption-server",
        strictSubcommandOptionsOrder = false
    )

    val username by parser.option(
        type = ArgType.String,
        fullName = "username",
        shortName = "u",
        description = "Username of user"
    ).required()
    val password by parser.option(
        type = ArgType.String,
        fullName = "password",
        shortName = "p",
        description = "Password of user"
    ).required()

    class NewUserCommand : Subcommand("newUser", "Create New User") {
        override fun execute() {
            runBlocking {
                createNewUser(createHttpClient(), username, password)
            }
        }
    }

    class DeleteUserCommand : Subcommand("deleteUser", "Delete Existing User") {
        override fun execute() {
            runBlocking {
                deleteExistingUser(
                    client = createAuthenticatedHttpClient(username = username, password = password)
                )
            }
        }
    }

    class UpdateUserPasswordCommand : Subcommand("updateUserPassword", "Update User Password") {
        val newPassword by parser.option(
            type = ArgType.String,
            fullName = "newPassword",
            shortName = "np",
            description = "The new password for the user"
        ).required()

        override fun execute() {
            runBlocking {
                updateUserPassword(
                    client = createAuthenticatedHttpClient(
                        username = username,
                        password = password
                    ),
                    newPassword = newPassword
                )
            }
        }
    }



    parser.subcommands(
        NewUserCommand(),
        DeleteUserCommand(),
        UpdateUserPasswordCommand()
    )

    parser.parse(args)
}