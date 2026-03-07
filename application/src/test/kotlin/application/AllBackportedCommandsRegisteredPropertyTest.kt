package application

import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Feature: specmatic-command-backport, Property 1: All backported commands are registered
// **Validates: Requirements 1.1**
class AllBackportedCommandsRegisteredPropertyTest {

    private val expectedCommandClasses: List<Class<*>> = listOf(
        BundleCommand::class.java,
        PushCommand::class.java,
        SubscribeCommand::class.java,
        CompatibleCommand::class.java,
        ValidateViaLogs::class.java,
        SamplesCommand::class.java,
        VirtualServiceCommand::class.java,
        ReDeclaredAPICommand::class.java,
        DifferenceCommand::class.java,
        InstallCommand::class.java,
        GraphCommand::class.java,
        MergeCommand::class.java,
        ToOpenAPICommand::class.java,
        // CentralContractRepoReportCommand is not directly registered — ReporterSubcommands
        // already provides a command with the same "central-contract-repo-report" name.
    )

    @Test
    fun `all backported commands have an instance in subcommands`() {
        val registeredCommands = SpecmaticCoreSubcommands.subcommands()

        runBlocking {
            checkAll(Exhaustive.collection(expectedCommandClasses)) { expectedClass ->
                val hasInstance = registeredCommands.any { expectedClass.isInstance(it) }
                assertThat(hasInstance)
                    .describedAs("${expectedClass.simpleName} should have an instance in SpecmaticCoreSubcommands.subcommands()")
                    .isTrue()
            }
        }
    }
}
