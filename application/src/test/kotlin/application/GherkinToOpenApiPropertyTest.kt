package application

import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.specmatic.core.parseGherkinStringToFeature
import io.swagger.v3.core.util.Yaml
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Feature: specmatic-command-backport, Property 9: Gherkin to OpenAPI round-trip validity
// **Validates: Requirements 11.1**
class GherkinToOpenApiPropertyTest {

    // A collection of known-valid Gherkin contract strings exercising different patterns
    private val validGherkinContracts = listOf(
        // Simple GET returning a number
        """
Feature: Simple GET API
  Scenario: Get a number
    When GET /number
    Then status 200
    And response-body (number)
        """.trimIndent(),

        // GET returning a JSON object
        """
Feature: Pet API
  Scenario: Get pet
    When GET /pet
    Then status 200
    And response-body
      | id   | (number) |
      | name | (string) |
        """.trimIndent(),

        // POST with request body and response body
        """
Feature: User API
  Scenario: Create user
    When POST /users
    And request-body
      | email | (string) |
      | name  | (string) |
    Then status 201
    And response-body
      | id    | (number) |
      | email | (string) |
      | name  | (string) |
        """.trimIndent(),

        // GET with query parameter
        """
Feature: Search API
  Scenario: Search items
    When GET /items?query=(string)
    Then status 200
    And response-body (string)
        """.trimIndent(),

        // GET with path parameter
        """
Feature: Item API
  Scenario: Get item by id
    When GET /items/(id:number)
    Then status 200
    And response-body
      | id   | (number) |
      | name | (string) |
        """.trimIndent(),

        // POST with string request and response
        """
Feature: Echo API
  Scenario: Echo text
    When POST /echo
    And request-body (string)
    Then status 200
    And response-body (string)
        """.trimIndent(),

        // GET with request header
        """
Feature: Auth API
  Scenario: Get with auth header
    When GET /secure
    And request-header Authorization (string)
    Then status 200
    And response-body (string)
        """.trimIndent(),

        // Multiple scenarios in one feature
        """
Feature: Multi Scenario API
  Scenario: List items
    When GET /things
    Then status 200
    And response-body (string)

  Scenario: Create item
    When POST /things
    And request-body (string)
    Then status 201
    And response-body (string)
        """.trimIndent()
    )

    @Test
    fun `parseGherkinStringToFeature followed by toOpenApi produces valid non-null YAML`() {
        runBlocking {
            checkAll(Exhaustive.collection(validGherkinContracts)) { gherkinContract ->
                val feature = parseGherkinStringToFeature(gherkinContract)
                val openApi = feature.toOpenApi()

                assertThat(openApi)
                    .describedAs("toOpenApi() should return a non-null OpenAPI object for contract: ${gherkinContract.lines().first()}")
                    .isNotNull()

                val yamlString = Yaml.pretty(openApi)

                assertThat(yamlString)
                    .describedAs("YAML output should be non-null and non-empty")
                    .isNotNull()
                    .isNotEmpty()

                // Validate it's parseable YAML by loading it with SnakeYAML (transitive dep)
                val yamlParser = org.yaml.snakeyaml.Yaml()
                val parsed = yamlParser.load<Any>(yamlString)

                assertThat(parsed)
                    .describedAs("YAML output should be parseable as valid YAML")
                    .isNotNull()

                // Verify it contains expected OpenAPI structure
                assertThat(parsed).isInstanceOf(Map::class.java)
                @Suppress("UNCHECKED_CAST")
                val yamlMap = parsed as Map<String, Any>
                assertThat(yamlMap).containsKey("openapi")
                assertThat(yamlMap).containsKey("paths")
            }
        }
    }
}
