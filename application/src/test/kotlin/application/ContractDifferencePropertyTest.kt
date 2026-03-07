package application

import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.Feature
import io.specmatic.core.Results
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.testBackwardCompatibility
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Feature: specmatic-command-backport, Property 7: Contract difference detection
// **Validates: Requirements 6.1**
class ContractDifferencePropertyTest {

    /**
     * Replicates the difference() function from DifferenceCommand
     * using the 2.42.0 testBackwardCompatibility() API (renamed from findDifferences in 2.7.1).
     */
    private fun difference(olderContract: Feature, newerContract: Feature): CompatibilityReport =
        try {
            testBackwardCompatibility(olderContract, newerContract).let { results ->
                when {
                    results.failureCount > 0 -> {
                        IncompatibleReport(results, "The two contracts are not similar.")
                    }
                    else -> CompatibleReport("The two contracts are similar.")
                }
            }
        } catch (e: ContractException) {
            ContractExceptionReport(e)
        } catch (e: Throwable) {
            ExceptionReport(e)
        }

    private fun featureFromYaml(yaml: String): Feature =
        OpenApiSpecification.fromYAML(yaml, "").toFeature()

    // A set of structurally distinct minimal OpenAPI specs
    private val specA = """
openapi: 3.0.3
info:
  title: Spec A
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
                    - name
                  properties:
                    id:
                      type: integer
                    name:
                      type: string
    """.trimIndent()

    private val specB = """
openapi: 3.0.3
info:
  title: Spec B
  version: '1.0'
paths:
  /users:
    post:
      summary: Create user
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - email
              properties:
                email:
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
                  - email
                properties:
                  id:
                    type: integer
                  email:
                    type: string
    """.trimIndent()

    private val specC = """
openapi: 3.0.3
info:
  title: Spec C
  version: '1.0'
paths:
  /items:
    get:
      summary: Get items
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: object
                required:
                  - count
                properties:
                  count:
                    type: integer
    delete:
      summary: Delete all items
      responses:
        '204':
          description: No Content
    """.trimIndent()

    private val specs = listOf(specA, specB, specC)
    private val features: List<Feature> by lazy { specs.map { featureFromYaml(it) } }

    private val arbFeatureIndex = Arb.of(0, 1, 2)

    @Test
    fun `identical features produce CompatibleReport`() {
        runBlocking {
            checkAll(100, arbFeatureIndex) { idx ->
                val feature = features[idx]
                val report = difference(feature, feature)
                assertThat(report)
                    .describedAs("Comparing feature $idx with itself should produce CompatibleReport")
                    .isInstanceOf(CompatibleReport::class.java)
                assertThat(report.exitCode).isEqualTo(0)
            }
        }
    }

    @Test
    fun `different features produce IncompatibleReport`() {
        // Generate pairs of distinct feature indices
        data class DistinctPair(val first: Int, val second: Int)

        val arbDistinctPair = Arb.choice(
            Arb.of(DistinctPair(0, 1)),
            Arb.of(DistinctPair(0, 2)),
            Arb.of(DistinctPair(1, 0)),
            Arb.of(DistinctPair(1, 2)),
            Arb.of(DistinctPair(2, 0)),
            Arb.of(DistinctPair(2, 1)),
        )

        runBlocking {
            checkAll(100, arbDistinctPair) { (idxA, idxB) ->
                val featureA = features[idxA]
                val featureB = features[idxB]
                val report = difference(featureA, featureB)
                assertThat(report)
                    .describedAs("Comparing feature $idxA with feature $idxB should produce IncompatibleReport")
                    .isInstanceOf(IncompatibleReport::class.java)
                assertThat(report.exitCode).isEqualTo(1)
            }
        }
    }
}
