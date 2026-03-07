package io.specmatic.core.examples.server

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.parseContractFileToFeature
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.mock.ScenarioStub
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

// Feature: examples-interactive-backport, Property 4: Example validation detects mismatches
// **Validates: Requirements 3.4**
class ExampleValidationPropertyTest {

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

    private fun createFeature(): io.specmatic.core.Feature {
        val specFile = tempDir.resolve("pet_api_${System.nanoTime()}.yaml").toFile()
        specFile.writeText(openApiSpec)
        return parseContractFileToFeature(specFile)
    }

    // Generator for valid pet IDs (positive integers)
    private val arbValidId: Arb<Int> = Arb.int(1..10000)

    // Generator for valid pet names (non-empty alphabetic strings)
    private val arbValidName: Arb<String> = Arb.string(1..20, Codepoint.alphanumeric())
        .filter { it.isNotBlank() && it[0].isLetter() }

    @Test
    fun `valid ScenarioStub conforming to spec produces successful validation`() {
        val feature = createFeature()

        runBlocking {
            checkAll(100, arbValidId, arbValidName) { id, name ->
                val request = HttpRequest(
                    method = "GET",
                    path = "/pets"
                )
                val responseBody = JSONObjectValue(
                    mapOf(
                        "id" to NumberValue(id),
                        "name" to StringValue(name)
                    )
                )
                val response = HttpResponse(
                    status = 200,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = responseBody
                )
                val stub = ScenarioStub(request = request, response = response)

                val results = ExamplesInteractiveServer.validateExample(feature, stub)

                assertThat(results.success())
                    .describedAs("Valid stub with id=$id, name='$name' should pass validation but got failures: ${results.report()}")
                    .isTrue()
            }
        }
    }

    // Generator for invalid type values to use as the "id" field (strings instead of integers)
    private val arbInvalidIdValue: Arb<String> = Arb.string(1..10, Codepoint.alphanumeric())
        .filter { it.isNotBlank() && it[0].isLetter() }

    @Test
    fun `invalid ScenarioStub with wrong types produces validation failures`() {
        val feature = createFeature()

        runBlocking {
            checkAll(100, arbInvalidIdValue, arbValidName) { invalidId, name ->
                val request = HttpRequest(
                    method = "GET",
                    path = "/pets"
                )
                // id should be integer but we provide a string
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

                val results = ExamplesInteractiveServer.validateExample(feature, stub)

                assertThat(results.hasFailures())
                    .describedAs("Stub with string id='$invalidId' should fail validation")
                    .isTrue()
                assertThat(results.success())
                    .describedAs("Stub with string id='$invalidId' should not be successful")
                    .isFalse()
                assertThat(results.report())
                    .describedAs("Failure report should contain a non-empty description")
                    .isNotBlank()
            }
        }
    }
}
