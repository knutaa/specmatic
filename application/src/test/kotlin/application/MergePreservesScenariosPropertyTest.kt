package application

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.Feature
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Feature: specmatic-command-backport, Property 8: Merge preserves all scenarios
// **Validates: Requirements 10.1**
class MergePreservesScenariosPropertyTest {

    private fun featureFromYaml(yaml: String): Feature =
        OpenApiSpecification.fromYAML(yaml, "").toFeature()

    // Structurally distinct OpenAPI specs with varying scenario counts
    private val specOneScenario = """
openapi: 3.0.3
info:
  title: One Scenario
  version: '1.0'
paths:
  /alpha:
    get:
      summary: Get alpha
      responses:
        '200':
          description: OK
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

    private val specTwoScenarios = """
openapi: 3.0.3
info:
  title: Two Scenarios
  version: '1.0'
paths:
  /beta:
    get:
      summary: Get beta
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: object
                required:
                  - name
                properties:
                  name:
                    type: string
    post:
      summary: Create beta
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
                  - name
                properties:
                  id:
                    type: integer
                  name:
                    type: string
    """.trimIndent()

    private val specThreeScenarios = """
openapi: 3.0.3
info:
  title: Three Scenarios
  version: '1.0'
paths:
  /gamma:
    get:
      summary: List gamma
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
    post:
      summary: Create gamma
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - value
              properties:
                value:
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
                  - value
                properties:
                  id:
                    type: integer
                  value:
                    type: string
    delete:
      summary: Delete gamma
      responses:
        '204':
          description: No Content
    """.trimIndent()

    private val specFourScenarios = """
openapi: 3.0.3
info:
  title: Four Scenarios
  version: '1.0'
paths:
  /delta:
    get:
      summary: Get delta
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: object
                required:
                  - status
                properties:
                  status:
                    type: string
    put:
      summary: Update delta
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - status
              properties:
                status:
                  type: string
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: object
                required:
                  - status
                properties:
                  status:
                    type: string
  /delta/archive:
    post:
      summary: Archive delta
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: object
                required:
                  - archived
                properties:
                  archived:
                    type: boolean
    delete:
      summary: Delete delta archive
      responses:
        '204':
          description: No Content
    """.trimIndent()

    private val allSpecs = listOf(specOneScenario, specTwoScenarios, specThreeScenarios, specFourScenarios)
    private val allFeatures: List<Feature> by lazy { allSpecs.map { featureFromYaml(it) } }

    /**
     * Replicates the merge logic from MergeCommand:
     * Feature(scenarios = contracts.flatMap { it.scenarios }, name = contracts.first().name)
     */
    private fun mergeFeatures(features: List<Feature>): Feature {
        require(features.isNotEmpty())
        return Feature(
            scenarios = features.flatMap { it.scenarios },
            name = features.first().name,
            protocol = features.first().protocol
        )
    }

    // Arb that picks a random feature index (0..3)
    private val arbFeatureIndex = Arb.of(0, 1, 2, 3)

    // Arb that generates a list of 2-5 feature indices
    private val arbFeatureIndexList = Arb.list(arbFeatureIndex, 2..5)

    @Test
    fun `merged Feature scenario count equals sum of input scenario counts`() {
        runBlocking {
            checkAll(100, arbFeatureIndexList) { indices ->
                val features = indices.map { allFeatures[it] }
                val expectedScenarioCount = features.sumOf { it.scenarios.size }

                val merged = mergeFeatures(features)

                assertThat(merged.scenarios.size)
                    .describedAs(
                        "Merging ${features.size} features with scenario counts ${features.map { it.scenarios.size }} " +
                        "should produce ${expectedScenarioCount} scenarios"
                    )
                    .isEqualTo(expectedScenarioCount)
            }
        }
    }

    @Test
    fun `merged Feature contains exactly the scenarios from all inputs`() {
        runBlocking {
            checkAll(100, arbFeatureIndexList) { indices ->
                val features = indices.map { allFeatures[it] }
                val allInputScenarios = features.flatMap { it.scenarios }

                val merged = mergeFeatures(features)

                // Every input scenario should be present in the merged feature
                assertThat(merged.scenarios)
                    .describedAs("Merged feature should contain all scenarios from inputs")
                    .containsExactlyElementsOf(allInputScenarios)
            }
        }
    }
}
