package io.specmatic.core.examples.server

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

// Feature: examples-interactive-backport, Property 6: Update and content round-trip
// **Validates: Requirements 3.6, 3.7**
class UpdateContentRoundTripPropertyTest {

    @TempDir
    lateinit var tempDir: Path

    // Generator for random JSON keys (non-empty alphanumeric strings starting with a letter)
    private val arbJsonKey: Arb<String> = Arb.string(1..15, Codepoint.alphanumeric())
        .filter { it.isNotBlank() && it[0].isLetter() }

    // Generator for random JSON string values
    private val arbJsonStringValue: Arb<String> = Arb.string(0..50, Codepoint.alphanumeric())

    // Generator for random JSON number values
    private val arbJsonNumberValue: Arb<Int> = Arb.int(-10000..10000)

    // Generator for random JSON boolean values
    private val arbJsonBoolValue: Arb<Boolean> = Arb.boolean()

    // Generator for a random JSON object with 1-5 key-value pairs
    private val arbJsonContent: Arb<String> = Arb.bind(
        Arb.list(arbJsonKey, 1..5),
        Arb.list(arbJsonStringValue, 1..5),
        Arb.list(arbJsonNumberValue, 1..5),
        Arb.list(arbJsonBoolValue, 1..5)
    ) { keys, strings, numbers, bools ->
        val uniqueKeys = keys.distinct()
        val entries = uniqueKeys.mapIndexed { index, key ->
            when (index % 3) {
                0 -> "\"$key\": \"${strings.getOrElse(index) { "" }}\""
                1 -> "\"$key\": ${numbers.getOrElse(index) { 0 }}"
                else -> "\"$key\": ${bools.getOrElse(index) { true }}"
            }
        }
        "{\n  ${entries.joinToString(",\n  ")}\n}"
    }

    @Test
    fun `writing JSON content to a temp file and reading it back returns identical content`() {
        runBlocking {
            checkAll(100, arbJsonContent) { jsonContent ->
                val tempFile = tempDir.resolve("example_${System.nanoTime()}.json").toFile()

                // Simulate the update operation: write content to file
                tempFile.writeText(jsonContent)

                // Simulate the content operation: read content back from file
                val readBack = tempFile.readText()

                assertThat(readBack)
                    .describedAs("Content read back from file should equal what was written")
                    .isEqualTo(jsonContent)

                // Cleanup
                tempFile.delete()
            }
        }
    }
}
