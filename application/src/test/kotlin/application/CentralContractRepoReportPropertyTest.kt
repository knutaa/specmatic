package application

import io.kotest.property.Arb
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.specmatic.reports.CentralContractRepoReport
import io.specmatic.reports.SpecificationOperation
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

// Feature: specmatic-command-backport, Property 11: Central contract repo report completeness
// **Validates: Requirements 15.1**
class CentralContractRepoReportPropertyTest {

    data class SpecEntry(
        val fileName: String,
        val yamlContent: String,
        val expectedOperations: List<SpecificationOperation>
    )

    private val specEntries = listOf(
        SpecEntry(
            fileName = "pets.yaml",
            yamlContent = """
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
            """.trimIndent(),
            expectedOperations = listOf(
                SpecificationOperation("/pets", "GET", 200)
            )
        ),
        SpecEntry(
            fileName = "users.yaml",
            yamlContent = """
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
                    - name
                  properties:
                    name:
                      type: string
    post:
      summary: Create user
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
            """.trimIndent(),
            expectedOperations = listOf(
                SpecificationOperation("/users", "GET", 200),
                SpecificationOperation("/users", "POST", 201)
            )
        ),
        SpecEntry(
            fileName = "orders.yml",
            yamlContent = """
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
  /orders/{orderId}:
    get:
      summary: Get order
      parameters:
        - name: orderId
          in: path
          required: true
          schema:
            type: integer
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: object
                required:
                  - id
                  - item
                properties:
                  id:
                    type: integer
                  item:
                    type: string
            """.trimIndent(),
            expectedOperations = listOf(
                SpecificationOperation("/orders", "POST", 201),
                SpecificationOperation("/orders/{orderId}", "GET", 200)
            )
        ),
        SpecEntry(
            fileName = "products.yaml",
            yamlContent = """
openapi: 3.0.3
info:
  title: Products API
  version: '1.0'
paths:
  /products:
    get:
      summary: List products
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
                    - sku
                  properties:
                    sku:
                      type: string
  /products/{productId}:
    delete:
      summary: Delete product
      parameters:
        - name: productId
          in: path
          required: true
          schema:
            type: string
      responses:
        '204':
          description: Deleted
            """.trimIndent(),
            expectedOperations = listOf(
                SpecificationOperation("/products", "GET", 200),
                SpecificationOperation("/products/{productId}", "DELETE", 204)
            )
        )
    )

    // Generate non-empty sets of indices (1 to 4 specs)
    private val arbSpecIndices = Arb.set(Arb.of(0, 1, 2, 3), 1..4)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `report contains one entry per parseable OpenAPI file`() {
        runBlocking {
            checkAll(100, arbSpecIndices) { indices ->
                val subset = indices.map { specEntries[it] }
                val dir = createTempSpecDir(subset)

                val report = CentralContractRepoReport().generate(dir.absolutePath)

                assertThat(report.specifications)
                    .describedAs(
                        "Report should have one entry per spec file for: ${subset.map { it.fileName }}"
                    )
                    .hasSize(subset.size)

                val reportFileNames = report.specifications.map { File(it.specification).name }.toSet()
                val expectedFileNames = subset.map { it.fileName }.toSet()

                assertThat(reportFileNames)
                    .describedAs("Report file names should match written spec files")
                    .isEqualTo(expectedFileNames)
            }
        }
    }

    @Test
    fun `each report entry has correct operations for its specification`() {
        runBlocking {
            checkAll(100, arbSpecIndices) { indices ->
                val subset = indices.map { specEntries[it] }
                val dir = createTempSpecDir(subset)

                val report = CentralContractRepoReport().generate(dir.absolutePath)

                for (entry in subset) {
                    val row = report.specifications.find { File(it.specification).name == entry.fileName }
                    assertThat(row)
                        .describedAs("Report should contain entry for ${entry.fileName}")
                        .isNotNull

                    val actualOps = row!!.operations.map {
                        SpecificationOperation(it.path, it.method, it.responseCode)
                    }.toSet()
                    val expectedOps = entry.expectedOperations.toSet()

                    assertThat(actualOps)
                        .describedAs(
                            "Operations for ${entry.fileName} should match expected"
                        )
                        .isEqualTo(expectedOps)
                }
            }
        }
    }

    private var dirCounter = 0L

    private fun createTempSpecDir(specs: List<SpecEntry>): File {
        val dir = tempDir.resolve("specs_${dirCounter++}").toFile()
        dir.mkdirs()
        for (spec in specs) {
            File(dir, spec.fileName).writeText(spec.yamlContent)
        }
        return dir
    }
}
