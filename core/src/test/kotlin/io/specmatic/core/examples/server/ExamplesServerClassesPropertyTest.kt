package io.specmatic.core.examples.server

import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Feature: examples-interactive-backport, Property 2: All supporting data classes are present
// **Validates: Requirements 4.1**
class ExamplesServerClassesPropertyTest {

    private val expectedClassNames = listOf(
        "ExamplePageRequest",
        "GenerateExampleRequest",
        "GenerateExampleResponse",
        "GenerateExample",
        "SaveExampleRequest",
        "ValidateExampleRequest",
        "ValidateExampleResponse",
        "ExampleValidationDetails",
        "ExampleValidationResult",
        "FixExampleRequest",
        "FixExampleResult",
        "FixExampleStatus",
        "ExampleTestRequest",
        "ExampleTestResponse",
        "Severity",
        "ValidateExampleResponseMap",
        "CustomJsonNodeFactory",
        "CustomParserFactory"
    )

    @Test
    fun `all supporting data classes are loadable from the examples server package`() {
        runBlocking {
            checkAll(Exhaustive.collection(expectedClassNames)) { className ->
                val fqcn = "io.specmatic.core.examples.server.$className"
                val clazz = Class.forName(fqcn)
                assertThat(clazz)
                    .describedAs("$className should be loadable from $fqcn")
                    .isNotNull()
                assertThat(clazz.simpleName)
                    .describedAs("Loaded class simple name should match $className")
                    .isEqualTo(className)
            }
        }
    }
}
