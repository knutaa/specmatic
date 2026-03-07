package application

import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.Feature
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Feature: specmatic-command-backport, Property 10: Redeclared API detection correctness
// **Validates: Requirements 13.1, 13.2**
class RedeclaredAPIDetectionPropertyTest {

    private fun featureFromYaml(yaml: String, filePath: String = ""): Feature =
        OpenApiSpecification.fromYAML(yaml, filePath).toFeature()

    // Spec with /pets endpoint
    private val specPets = """
openapi: 3.0.3
info:
  title: Pets API
  version: '1.0'
paths:
  /pets:
    get:
      summary: List pets
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: array
                items:
                  type: object
                  required:
                    - id
                  properties:
                    id:
                      type: integer
    """.trimIndent()

    // Spec with /users endpoint
    private val specUsers = """
openapi: 3.0.3
info:
  title: Users API
  version: '1.0'
paths:
  /users:
    get:
      summary: List users
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: array
                items:
                  type: object
                  required:
                    - id
                  properties:
                    id:
                      type: integer
    """.trimIndent()

    // Spec with /orders endpoint
    private val specOrders = """
openapi: 3.0.3
info:
  title: Orders API
  version: '1.0'
paths:
  /orders:
    post:
      summary: Create order
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - item
              properties:
                item:
                  type: string
      responses:
        '201':
          description: Created
          content:
            application/json:
              schema:
                type: object
                required:
                  - id
                properties:
                  id:
                    type: integer
    """.trimIndent()

    // Spec with BOTH /pets and /users endpoints (overlaps with specPets and specUsers)
    private val specPetsAndUsers = """
openapi: 3.0.3
info:
  title: Pets and Users API
  version: '1.0'
paths:
  /pets:
    get:
      summary: List pets
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: array
                items:
                  type: object
                  required:
                    - id
                  properties:
                    id:
                      type: integer
  /users:
    get:
      summary: List users
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: array
                items:
                  type: object
                  required:
                    - id
                  properties:
                    id:
                      type: integer
    """.trimIndent()

    // Spec with /pets and /orders (overlaps with specPets and specOrders)
    private val specPetsAndOrders = """
openapi: 3.0.3
info:
  title: Pets and Orders API
  version: '1.0'
paths:
  /pets:
    get:
      summary: List pets
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: array
                items:
                  type: object
                  required:
                    - id
                  properties:
                    id:
                      type: integer
  /orders:
    post:
      summary: Create order
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - item
              properties:
                item:
                  type: string
      responses:
        '201':
          description: Created
          content:
            application/json:
              schema:
                type: object
                required:
                  - id
                properties:
                  id:
                    type: integer
    """.trimIndent()

    /**
     * Each entry: (yamlSpec, filePath, set of URL paths in the spec)
     */
    data class ContractEntry(val yaml: String, val filePath: String, val urlPaths: Set<String>)

    private val contractEntries = listOf(
        ContractEntry(specPets, "/repo/pets.yaml", setOf("/pets")),
        ContractEntry(specUsers, "/repo/users.yaml", setOf("/users")),
        ContractEntry(specOrders, "/repo/orders.yaml", setOf("/orders")),
        ContractEntry(specPetsAndUsers, "/repo/pets-users.yaml", setOf("/pets", "/users")),
        ContractEntry(specPetsAndOrders, "/repo/pets-orders.yaml", setOf("/pets", "/orders")),
    )

    private val features: List<Feature> by lazy {
        contractEntries.map { featureFromYaml(it.yaml, it.filePath) }
    }

    private val arbContractIndex = Arb.of(0, 1, 2, 3, 4)

    // Generate lists of 2-5 contract indices to form the input to findReDeclarationsAmongstContracts
    private val arbContractIndexList = Arb.list(arbContractIndex, 2..5)

    /**
     * Computes the expected redeclaration map from a list of contract entries.
     * A URL path is redeclared if it appears in more than one file.
     */
    private fun expectedRedeclarations(indices: List<Int>): Map<String, List<String>> {
        // Build: urlPath -> list of filePaths that contain it
        val pathToFiles = mutableMapOf<String, MutableList<String>>()
        for (idx in indices) {
            val entry = contractEntries[idx]
            for (urlPath in entry.urlPaths) {
                pathToFiles.getOrPut(urlPath) { mutableListOf() }.add(entry.filePath)
            }
        }
        // Only keep paths appearing in more than one file
        return pathToFiles.filter { (_, files) -> files.size > 1 }
    }

    @Test
    fun `redeclaration map keys are exactly the URL paths appearing in multiple files`() {
        runBlocking {
            checkAll(100, arbContractIndexList) { indices ->
                val contracts = indices.map { idx ->
                    Pair(features[idx], contractEntries[idx].filePath)
                }

                val result = findReDeclarationsAmongstContracts(contracts)
                val expected = expectedRedeclarations(indices)

                assertThat(result.keys)
                    .describedAs(
                        "For contract indices $indices, redeclaration keys should be URL paths in multiple files"
                    )
                    .containsExactlyInAnyOrderElementsOf(expected.keys)
            }
        }
    }

    @Test
    fun `redeclaration map values are the correct file lists for each overlapping path`() {
        runBlocking {
            checkAll(100, arbContractIndexList) { indices ->
                val contracts = indices.map { idx ->
                    Pair(features[idx], contractEntries[idx].filePath)
                }

                val result = findReDeclarationsAmongstContracts(contracts)
                val expected = expectedRedeclarations(indices)

                for ((urlPath, expectedFiles) in expected) {
                    assertThat(result).containsKey(urlPath)
                    assertThat(result[urlPath])
                        .describedAs(
                            "For URL path '$urlPath' with indices $indices, file list should match"
                        )
                        .containsExactlyInAnyOrderElementsOf(expectedFiles)
                }
            }
        }
    }

    @Test
    fun `unique paths not appearing in multiple files are absent from redeclaration map`() {
        runBlocking {
            checkAll(100, arbContractIndexList) { indices ->
                val contracts = indices.map { idx ->
                    Pair(features[idx], contractEntries[idx].filePath)
                }

                val result = findReDeclarationsAmongstContracts(contracts)

                // Compute paths that appear in exactly one file
                val pathToFiles = mutableMapOf<String, MutableList<String>>()
                for (idx in indices) {
                    val entry = contractEntries[idx]
                    for (urlPath in entry.urlPaths) {
                        pathToFiles.getOrPut(urlPath) { mutableListOf() }.add(entry.filePath)
                    }
                }
                val uniquePaths = pathToFiles.filter { (_, files) -> files.size == 1 }.keys

                for (uniquePath in uniquePaths) {
                    assertThat(result)
                        .describedAs(
                            "Unique path '$uniquePath' should NOT be in redeclaration map for indices $indices"
                        )
                        .doesNotContainKey(uniquePath)
                }
            }
        }
    }
}
