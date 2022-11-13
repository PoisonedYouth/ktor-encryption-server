import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli
import operations.NewUserCommand

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    val parser = ArgParser("ktor-encryption-server")

    parser.subcommands(NewUserCommand(parser))
    parser.parse(args)
}