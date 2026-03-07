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

// Feature: examples-interactive-backport, Property 8: Bulk mode generation skips existing examples
// **Validates: Requirements 8.3**
class BulkModeGenerationPropertyTest {

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
    fun `bulk mode generation skips existing examples and produces no duplicates`() {
        // Extract (method, path, status) triples from the spec
        val setupDir = Files.createTempDirectory("bulk_prop8_setup_").toFile()
        val setupSpecFile = File(setupDir, "pet_api.yaml")
        setupSpecFile.writeText(openApiSpec)
        val feature = parseContractFileToFeature(setupSpecFile)

        val matchingTriples = feature.scenarios
            .filter { !it.isNegative }
            .map { Triple(it.method, it.path, it.status) }
            .distinct()

        setupDir.deleteRecursively()

        assertThat(matchingTriples).isNotEmpty

        runBlocking {
            checkAll(100, Exhaustive.collection(matchingTriples)) { (method, path, statusCode) ->
                val tempDir = Files.createTempDirectory("bulk_prop8_").toFile()
                try {
                    val specFile = File(tempDir, "pet_api.yaml")
                    specFile.writeText(openApiSpec)

                    // Step 1: Generate examples without bulk mode to pre-populate
                    val initialResult = ExamplesInteractiveServer.generate(
                        contractFile = specFile,
                        method = method,
                        path = path,
                        responseStatusCode = statusCode,
                        contentType = null,
                        bulkMode = false,
                        allowOnlyMandatoryKeysInJSONObject = false
                    )

                    assertThat(initialResult)
                        .describedAs("Initial generate($method, $path, $statusCode) should produce examples")
                        .isNotEmpty

                    val initialCreatedCount = initialResult.count { it.created }
                    assertThat(initialCreatedCount)
                        .describedAs("Initial generation should create at least one example")
                        .isGreaterThan(0)

                    // Collect the files that exist after initial generation
                    val examplesDir = ExamplesInteractiveServer.getExamplesDirPath(specFile)
                    val filesAfterInitial = examplesDir.listFiles()
                        ?.filter { it.extension == "json" }
                        ?.map { it.name }
                        ?.toSet()
                        ?: emptySet()

                    // Step 2: Generate again with bulkMode=true
                    val bulkResult = ExamplesInteractiveServer.generate(
                        contractFile = specFile,
                        method = method,
                        path = path,
                        responseStatusCode = statusCode,
                        contentType = null,
                        bulkMode = true,
                        allowOnlyMandatoryKeysInJSONObject = false
                    )

                    // Verify: bulk mode should not create any new examples
                    val bulkCreatedCount = bulkResult.count { it.created }
                    assertThat(bulkCreatedCount)
                        .describedAs("Bulk mode generate($method, $path, $statusCode) should not create new examples when they already exist")
                        .isEqualTo(0)

                    // Verify: bulk mode newly created count <= non-bulk created count
                    assertThat(bulkCreatedCount)
                        .describedAs("Bulk mode should create fewer or equal examples compared to non-bulk mode")
                        .isLessThanOrEqualTo(initialCreatedCount)

                    // Verify: no duplicate example files in the examples directory
                    val filesAfterBulk = examplesDir.listFiles()
                        ?.filter { it.extension == "json" }
                        ?.map { it.name }
                        ?: emptyList()

                    assertThat(filesAfterBulk)
                        .describedAs("No duplicate example files should exist after bulk mode generation for ($method, $path, $statusCode)")
                        .doesNotHaveDuplicates()

                    // Verify: file count should not increase after bulk mode
                    assertThat(filesAfterBulk.size)
                        .describedAs("File count should not increase after bulk mode generation")
                        .isEqualTo(filesAfterInitial.size)
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }
    }
}
