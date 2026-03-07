package io.specmatic.core.examples.server

import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

// Feature: examples-interactive-backport, Property 3: No references to deprecated 2.7.1 type names
// **Validates: Requirements 4.2, 4.3, 6.1, 6.2**
class NoDeprecatedRefsPropertyTest {

    private val sourceDir = File("src/main/kotlin/io/specmatic/core/examples/server")

    private val backportedFiles: List<File> by lazy {
        sourceDir.listFiles { file -> file.extension == "kt" }?.toList()
            ?: error("Could not list .kt files in $sourceDir")
    }

    @Test
    fun `no backported source file references the deprecated InteractiveExamplesMismatchMessages type`() {
        assertThat(backportedFiles).isNotEmpty
        runBlocking {
            checkAll(Exhaustive.collection(backportedFiles)) { file ->
                val content = file.readText()
                assertThat(content)
                    .describedAs("${file.name} should not reference deprecated InteractiveExamplesMismatchMessages")
                    .doesNotContain("InteractiveExamplesMismatchMessages")
            }
        }
    }

    @Test
    fun `no backported source file declares an inner ScenarioFilter class inside ExamplesInteractiveServer`() {
        assertThat(backportedFiles).isNotEmpty
        runBlocking {
            checkAll(Exhaustive.collection(backportedFiles)) { file ->
                val content = file.readText()
                // Detect inner class ScenarioFilter declared within ExamplesInteractiveServer
                val innerScenarioFilterPattern = Regex(
                    """class\s+ExamplesInteractiveServer[\s\S]*?class\s+ScenarioFilter"""
                )
                assertThat(innerScenarioFilterPattern.containsMatchIn(content))
                    .describedAs("${file.name} should not declare an inner ScenarioFilter class within ExamplesInteractiveServer")
                    .isFalse()
            }
        }
    }
}
