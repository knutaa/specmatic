package io.specmatic.core.examples.server

import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.specmatic.core.parseContractFileToFeature
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

// Feature: examples-interactive-backport, Property 7: Generate produces examples for matching scenarios
// **Validates: Requirements 8.1, 8.4**
class GenerateExamplePropertyTest {

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
    post:
      summary: Create a pet
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - name
              properties:
                name:
                  type: string
      responses:
        '201':
          description: Pet created
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
  /pets/{id}:
    get:
      summary: Get a pet by ID
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
      responses:
        '200':
          description: A single pet
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
        '404':
          description: Pet not found
          content:
            application/json:
              schema:
                type: object
                required:
                  - message
                properties:
                  message:
                    type: string
    """.trimIndent()

    @Test
    fun `generate produces non-empty results with valid paths for each matching scenario`() {
        // Parse the spec once to extract the actual (method, path, status) triples from scenarios
        val setupDir = Files.createTempDirectory("gen_prop7_setup_").toFile()
        val setupSpecFile = File(setupDir, "pet_api.yaml")
        setupSpecFile.writeText(openApiSpec)
        val feature = parseContractFileToFeature(setupSpecFile)

        val matchingTriples = feature.scenarios
            .filter { !it.isNegative }
            .map { Triple(it.method, it.path, it.status) }
            .distinct()

        setupDir.deleteRecursively()

        assertThat(matchingTriples)
            .describedAs("Should have at least 4 scenarios from the spec")
            .hasSizeGreaterThanOrEqualTo(4)

        runBlocking {
            checkAll(100, Exhaustive.collection(matchingTriples)) { (method, path, statusCode) ->
                val tempDir = Files.createTempDirectory("gen_prop7_").toFile()
                try {
                    val specFile = File(tempDir, "pet_api.yaml")
                    specFile.writeText(openApiSpec)

                    val result = ExamplesInteractiveServer.generate(
                        contractFile = specFile,
                        method = method,
                        path = path,
                        responseStatusCode = statusCode,
                        contentType = null,
                        bulkMode = false,
                        allowOnlyMandatoryKeysInJSONObject = false
                    )

                    assertThat(result)
                        .describedAs("generate($method, $path, $statusCode) should return a non-empty list")
                        .isNotEmpty

                    result.forEach { examplePathInfo ->
                        assertThat(examplePathInfo.path)
                            .describedAs("ExamplePathInfo.path should be non-blank for ($method, $path, $statusCode)")
                            .isNotBlank()

                        val exampleFile = File(examplePathInfo.path)
                        assertThat(exampleFile.exists())
                            .describedAs("Generated example file should exist: ${examplePathInfo.path}")
                            .isTrue()

                        assertThat(examplePathInfo.created)
                            .describedAs("ExamplePathInfo.created should be true for freshly generated example")
                            .isTrue()
                    }
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }
    }
}
