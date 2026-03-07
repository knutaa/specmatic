package application

import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import picocli.CommandLine.Option

// Feature: examples-interactive-backport, Property 1: Interactive subcommand declares all required CLI options
// **Validates: Requirements 1.3**
class InteractiveSubcommandPropertyTest {

    private val requiredOptionNames = listOf(
        "--contract-file",
        "--filter",
        "--filter-name",
        "--filter-not-name",
        "--debug",
        "--dictionary",
        "--testBaseURL",
        "--allow-only-mandatory-keys-in-payload"
    )

    @Test
    fun `Interactive subcommand declares all required CLI options`() {
        runBlocking {
            checkAll(Exhaustive.collection(requiredOptionNames)) { optionName ->
                val interactiveClass = ExamplesCommand.Interactive::class.java
                val matchingField = interactiveClass.declaredFields.firstOrNull { field ->
                    val optionAnnotation = field.getAnnotation(Option::class.java)
                    optionAnnotation != null && optionName in optionAnnotation.names
                }
                assertThat(matchingField)
                    .describedAs("Interactive class should have a field with @Option annotation containing '$optionName'")
                    .isNotNull()
            }
        }
    }
}
