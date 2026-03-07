package application

import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import picocli.CommandLine

// Feature: specmatic-command-backport, Property 2: CLI dispatch resolves backported command names
// **Validates: Requirements 1.2, 1.3**
class CliDispatchResolvesBackportedCommandsPropertyTest {

    private val commandNameToClass: List<Pair<String, Class<*>>> = listOf(
        "bundle" to BundleCommand::class.java,
        "push" to PushCommand::class.java,
        "subscribe" to SubscribeCommand::class.java,
        "compatible" to CompatibleCommand::class.java,
        "validate-via-logs" to ValidateViaLogs::class.java,
        "samples" to SamplesCommand::class.java,
        "virtual-service" to VirtualServiceCommand::class.java,
        "redeclared" to ReDeclaredAPICommand::class.java,
        "similar" to DifferenceCommand::class.java,
        "install" to InstallCommand::class.java,
        "graph" to GraphCommand::class.java,
        "merge" to MergeCommand::class.java,
        "to-openapi" to ToOpenAPICommand::class.java,
        // "central-contract-repo-report" is already registered by ReporterSubcommands,
        // so we don't duplicate it here to avoid DuplicateNameException.
    )

    @Test
    fun `CLI dispatch resolves each backported command name to the correct class`() {
        val commandLine = CommandLine(SpecmaticCommand())
        SpecmaticCoreSubcommands.configure(commandLine)
        val subcommands = commandLine.subcommands

        runBlocking {
            checkAll(Exhaustive.collection(commandNameToClass)) { (name, expectedClass) ->
                assertThat(subcommands).containsKey(name)
                val resolved = subcommands[name]!!.getCommand<Any>()
                assertThat(resolved)
                    .`as`("Command name '$name' should resolve to ${expectedClass.simpleName}")
                    .isInstanceOf(expectedClass)
            }
        }
    }
}
