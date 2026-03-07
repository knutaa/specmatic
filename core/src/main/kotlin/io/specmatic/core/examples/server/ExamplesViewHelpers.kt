package io.specmatic.core.examples.server

import io.specmatic.conversions.ExampleFromFile
import io.specmatic.core.BreadCrumb
import io.specmatic.core.Feature
import io.specmatic.core.METHOD_BREAD_CRUMB
import io.specmatic.core.Result
import io.specmatic.core.Scenario
import io.specmatic.core.log.consoleDebug
import io.specmatic.core.utilities.exceptionCauseMessage
import java.io.File

/**
 * Standalone helper functions used by ExamplesView (and later by ExamplesInteractiveServer).
 * These are extracted so that ExamplesView compiles before ExamplesInteractiveServer is backported.
 */

fun File.getExamplesFromDir(): List<ExampleFromFile> {
    return this.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull {
        ExampleFromFile.fromFile(it).realise(
            hasValue = { example, _ -> example },
            orException = { err -> consoleDebug(exceptionCauseMessage(err.t)); null },
            orFailure = { null }
        )
    }
}

fun getExistingExampleFiles(
    feature: Feature,
    scenario: Scenario,
    examples: List<ExampleFromFile>
): List<Pair<ExampleFromFile, Result>> {
    return examples.mapNotNull { example ->
        val matchResult = scenario.matches(
            httpRequest = example.request,
            httpResponse = example.response,
            mismatchMessages = ExampleMismatchMessages,
            flagsBased = feature.flagsBased
        )

        when (matchResult) {
            is Result.Success -> example to matchResult
            is Result.Failure -> {
                val isFailureRelatedToScenario = matchResult.getFailureBreadCrumbs("").none { breadCrumb ->
                    breadCrumb.contains(BreadCrumb.PATH.value)
                            || breadCrumb.contains(METHOD_BREAD_CRUMB)
                            || breadCrumb.contains("REQUEST.HEADERS.Content-Type")
                            || breadCrumb.contains("STATUS")
                }
                if (isFailureRelatedToScenario) { example to matchResult } else null
            }
        }
    }
}
