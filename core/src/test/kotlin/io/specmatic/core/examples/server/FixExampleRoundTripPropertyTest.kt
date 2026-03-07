package io.specmatic.core.examples.server

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.specmatic.core.EXAMPLES_DIR_SUFFIX
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import io.specmatic.mock.ScenarioStub
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

// Feature: examples-interactive-backport, Property 5: Fix then validate round-trip
// **Validates: Requirements 3.5**
class FixExampleRoundTripPropertyTest {

    @TempDir
    lateinit var tempDir: Path

    private val openApiSpec = """
openapi: 3.0.3
info:
  title: Pet API
  version: '1.0'
paths:
  /pets:
    get:
      summary: List pets
      responses:
        '200':
          description: A list of pets
          content:
            application/json:
              schema:
                type: object
                required:
                  - id
                  - name
                properties:
                  id:
                    type: integer
                  name:
                    type: string
    """.trimIndent()

    private fun createSpecFile(suffix: String = ""): File {
        val specFile = tempDir.resolve("pet_api${suffix}.yaml").toFile()
        specFile.writeText(openApiSpec)
        return specFile
    }

    private fun createExamplesDir(specFile: File): File {
        val examplesDir = specFile.parentFile.resolve("${specFile.nameWithoutExtension}$EXAMPLES_DIR_SUFFIX")
        examplesDir.mkdirs()
        return examplesDir
    }

    // Generator for invalid id values (non-empty alphabetic strings that cannot be parsed as integers)
    private val arbInvalidId: Arb<String> = Arb.string(1..10, Codepoint.alphanumeric())
        .filter { it.isNotBlank() && it[0].isLetter() }

    // Generator for valid pet names
    private val arbValidName: Arb<String> = Arb.string(1..20, Codepoint.alphanumeric())
        .filter { it.isNotBlank() && it[0].isLetter() }

    @Test
    fun `fixing an invalid example then validating it produces success`() {
        runBlocking {
            var iteration = 0
            checkAll(100, arbInvalidId, arbValidName) { invalidId, name ->
                iteration++
                val specFile = createSpecFile("_$iteration")
                val feature = parseContractFileToFeature(specFile)
                val examplesDir = createExamplesDir(specFile)

                // Create an invalid example: id should be integer but we use a string
                val request = HttpRequest(method = "GET", path = "/pets")
                val responseBody = JSONObjectValue(
                    mapOf(
                        "id" to StringValue(invalidId),
                        "name" to StringValue(name)
                    )
                )
                val response = HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = responseBody
                )
                val stub = ScenarioStub(request = request, response = response)
                val exampleFile = examplesDir.resolve("example_$iteration.json")
                exampleFile.writeText(stub.toJSON().toStringLiteral())

                // Fix the invalid example
                val fixResult = ExamplesInteractiveServer.fixExample(feature, exampleFile)

                assertThat(fixResult.status)
                    .describedAs("Fix should succeed for invalid example with string id='$invalidId', name='$name'")
                    .isEqualTo(FixExampleStatus.SUCCEDED)

                // Validate the fixed example
                val validationResult = ExamplesInteractiveServer.validateExample(specFile, exampleFile)

                assertThat(validationResult)
                    .describedAs("Validation after fix should succeed for id='$invalidId', name='$name'")
                    .isInstanceOf(Result.Success::class.java)
            }
        }
    }
}
