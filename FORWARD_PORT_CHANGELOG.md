# Forward-Port Changelog: specmatic-2.42.0 → specmatic-main

This document describes all changes made to `specmatic-main` as part of the forward-port from the `specmatic-2.42.0` fork. The fork contained two feature sets developed via prior specs: an interactive examples web server with CLI subcommand, and 13 backported CLI commands with supporting core classes and property tests.

## API Adaptation Rule

The fork uses `HttpStubFilterContext` where Main uses `ExampleFilterContext` (same logic, renamed class). All copied files were checked for `HttpStubFilterContext` references; none of the forward-ported files contained any, so no textual adaptation was required.

---

## 1. Files Modified in Main

These files already existed in `specmatic-main` and were updated to support the backported functionality.

### 1.1 `application/src/main/kotlin/application/SpecmaticCommand.kt`

**Change**: Extended `SpecmaticCoreSubcommands.subcommands()` to register 13 backported CLI commands.

Added after the existing `*ReporterSubcommands.subcommands()` entry:

```kotlin
// Backported commands
DifferenceCommand(),
InstallCommand(),
GraphCommand(),
MergeCommand(),
ToOpenAPICommand(),
ReDeclaredAPICommand(),
BundleCommand(),
PushCommand(),
SubscribeCommand(),
CompatibleCommand(),
ValidateViaLogs(),
SamplesCommand(),
VirtualServiceCommand(),
```

`CentralContractRepoReportCommand` is intentionally **not** registered here because `ReporterSubcommands` already provides a command with the same CLI name (`central-contract-repo-report`).

### 1.2 `application/src/main/kotlin/application/ExamplesCommand.kt`

**Change**: Added the `Interactive` subcommand for the interactive examples web server.

- `@Command(subcommands = [...])` annotation updated from `[Validate::class]` to `[Validate::class, Interactive::class]`
- Added `Interactive` inner class implementing `Callable<Unit>` with 8 picocli `@Option` fields:
  - `--contract-file`, `--filter`, `--filter-name`, `--filter-not-name`, `--debug`, `--dictionary`, `--testBaseURL`, `--allow-only-mandatory-keys-in-payload`
- The `Interactive.call()` method starts an `ExamplesInteractiveServer` on port 9001 and registers a shutdown hook
- Added imports: `ExamplesInteractiveServer`, `StringLog`, `consoleLog`, `consolePrintableURL`, `exceptionCauseMessage`, `exitWithMessage`, `Thread.sleep`

### 1.3 `core/src/main/kotlin/io/specmatic/core/SpecmaticConfig.kt` (interface)

**Change**: Added two default-implemented members to the `SpecmaticConfig` interface.

```kotlin
val configFilePath: String
    get() = Configuration.configFilePath

fun contractTestPathData(useCurrentBranchForCentralRepo: Boolean = false): List<ContractPathData> {
    return contractFilePathsFrom(
        Configuration.configFilePath,
        DEFAULT_WORKING_DIRECTORY,
        useCurrentBranchForCentralRepo
    ) { source -> source.testContracts }
}
```

These are consumed by `BundleCommand` and `CentralContractRepoReportCommand`.

### 1.4 `application/src/main/kotlin/application/SpecmaticConfig.kt` (application-level class)

**Change**: Added `configFilePath` property and `contractTestPathData()` method to the application-level `SpecmaticConfig` class (which shadows the core interface for local use by command files).

Also added `contractStubPaths()`, `contractTestPaths()`, and `contractStubPathData()` utility methods used by various backported commands.

### 1.5 `core/build.gradle.kts`

**Change**: Added Kotest property testing dependency required by the forward-ported property tests:

```kotlin
testImplementation("io.kotest:kotest-property-jvm:6.1.2")
```

### 1.6 `application/build.gradle.kts`

**Change**: Added Kotest property testing dependencies:

```kotlin
testImplementation("io.kotest:kotest-assertions-core-jvm:6.1.2")
testImplementation("io.kotest:kotest-property-jvm:6.1.2")
```

---

## 2. New Files Added to Main

### 2.1 Core Module — Interactive Examples Server (22 files)

Location: `core/src/main/kotlin/io/specmatic/core/examples/server/`

These files implement the interactive examples web server that powers the `examples interactive` CLI subcommand. Four files (`ExampleMismatchMessages.kt`, `FixExampleResponse.kt`, `ScenarioFilter.kt`, `SchemaExample.kt`) already existed in Main and were not overwritten.

| File | Purpose |
|---|---|
| `CustomJsonNodeFactory.kt` | Custom Jackson node factory for JSON line-number tracking |
| `CustomParserFactory.kt` | Custom Jackson parser factory |
| `ExamplePageRequest.kt` | Request DTO for example page loading |
| `ExamplesInteractiveServer.kt` | Ktor-based interactive server (main entry point) |
| `ExamplesView.kt` | View model for the examples UI |
| `ExamplesViewHelpers.kt` | Helper object for view rendering |
| `ExampleTestRequest.kt` | Request DTO for example testing |
| `ExampleTestResponse.kt` | Response DTO for example testing |
| `ExampleValidationDetails.kt` | Validation detail data class |
| `ExampleValidationResult.kt` | Validation result data class |
| `FindLineNumber.kt` | Utility functions for JSON line-number lookup |
| `FixExampleRequest.kt` | Request DTO for example fixing |
| `FixExampleResult.kt` | Result data class for example fixing |
| `GenerateExample.kt` | Data class for generated examples |
| `GenerateExampleRequest.kt` | Request DTO for example generation |
| `GenerateExampleResponse.kt` | Response DTO for example generation |
| `SaveExampleRequest.kt` | Request DTO for saving examples |
| `SchemaExamplesView.kt` | View model for schema-level examples |
| `Severity.kt` | Enum for validation severity levels |
| `ValidateExampleRequest.kt` | Request DTO for example validation |
| `ValidateExampleResponse.kt` | Response DTO for example validation |
| `ValidateExampleResponseMap.kt` | Map wrapper for validation responses |

### 2.2 Core Module — Web Resources (6 files)

These files provide the Thymeleaf template and static assets for the interactive examples UI.

| File | Purpose |
|---|---|
| `core/src/main/resources/templates/examples/index.html` | Thymeleaf HTML template for the examples UI |
| `core/src/main/resources/static/codemirror-bundle.esm.js` | CodeMirror editor bundle |
| `core/src/main/resources/static/example-server.js` | Client-side JavaScript for the examples server |
| `core/src/main/resources/static/json-source-map.js` | JSON source map utility |
| `core/src/main/resources/static/layout.css` | Layout stylesheet |
| `core/src/main/resources/static/setup.css` | Setup stylesheet |

### 2.3 Core Module — Stateful Stub (2 files)

Location: `core/src/main/kotlin/io/specmatic/stub/stateful/`

| File | Purpose |
|---|---|
| `StatefulHttpStub.kt` | Ktor-based stateful CRUD stub server with caching, PATCH/POST/GET/DELETE support, and accepted-response (202) handling |
| `StubCache.kt` | Thread-safe in-memory cache for stub responses with CRUD operations, filtering, and attribute selection |

### 2.4 Core Module — Reports (2 files)

Location: `core/src/main/kotlin/io/specmatic/reports/`

| File | Purpose |
|---|---|
| `CentralContractRepoReport.kt` | Generates a report of all OpenAPI specifications found in a directory tree |
| `CentralContractRepoReportJson.kt` | Data classes: `CentralContractRepoReportJson`, `SpecificationRow`, `SpecificationOperation` |

### 2.5 Core Module — Discriminator Support (1 file)

Location: `core/src/main/kotlin/io/specmatic/core/discriminator/`

| File | Purpose |
|---|---|
| `DiscriminatorExampleInjector.kt` | Injects discriminator-based descriptions into generated examples (used by `ExamplesInteractiveServer`) |

### 2.6 Core Module — Contract (1 file)

Location: `core/src/main/kotlin/io/specmatic/core/`

| File | Purpose |
|---|---|
| `Contract.kt` | Data class wrapping `Feature` with `samples()` method for running contract tests (used by `SamplesCommand`) |

### 2.7 Application Module — Command Files (14 files)

Location: `application/src/main/kotlin/application/`

| File | CLI Command Name | Purpose |
|---|---|---|
| `DifferenceCommand.kt` | `similar` | Compare two API specifications for differences |
| `InstallCommand.kt` | `install` | Install contract files from a repository |
| `GraphCommand.kt` | `graph` | Generate a dependency graph of API specifications |
| `MergeCommand.kt` | `merge` | Merge multiple API specifications into one |
| `ToOpenAPICommand.kt` | `to-openapi` | Convert Gherkin contracts to OpenAPI format |
| `RedeclaredAPICommand.kt` | `redeclared` | Detect redeclared API endpoints across specifications |
| `BundleCommand.kt` | `bundle` | Bundle contract files into a ZIP archive |
| `PushCommand.kt` | `push` | Push contract files to a repository |
| `SubscribeCommand.kt` | `subscribe` | Subscribe to contract changes |
| `CompatibleCommand.kt` | `compatible` | Check backward compatibility between specifications |
| `ValidateViaLogs.kt` | `validate-via-logs` | Validate API conformance using logged traffic |
| `SamplesCommand.kt` | `samples` | Generate sample requests from a contract |
| `VirtualServiceCommand.kt` | `virtual-service` | Run a virtual service stub from specifications |
| `CentralContractRepoReportCommand.kt` | `central-contract-repo-report` | Generate a central contract repository report (not registered in subcommands — provided by `ReporterSubcommands`) |

### 2.8 Application Module — Support Files (5 files)

Location: `application/src/main/kotlin/application/`

| File | Purpose |
|---|---|
| `ContractToCheck.kt` | Wrapper for contract files with Git-aware operations |
| `FileOperations.kt` | File I/O utility class |
| `Outcome.kt` | Result monad for operation outcomes |
| `PartialCommitFetch.kt` | Git commit content fetcher |
| `Zipper.kt` | ZIP archive creation utility |

---

## 3. Property Tests Added

### 3.1 Core — Examples Server Property Tests (6 files)

Location: `core/src/test/kotlin/io/specmatic/core/examples/server/`

| File | What It Tests |
|---|---|
| `NoDeprecatedRefsPropertyTest.kt` | No deprecated `$ref` patterns in generated examples |
| `ExampleValidationPropertyTest.kt` | Example validation correctness across generated inputs |
| `FixExampleRoundTripPropertyTest.kt` | Fix-example round-trip consistency |
| `UpdateContentRoundTripPropertyTest.kt` | Content update round-trip consistency |
| `GenerateExamplePropertyTest.kt` | Example generation produces valid outputs |
| `BulkModeGenerationPropertyTest.kt` | Bulk generation mode correctness |

### 3.2 Core — Stub Cache Property Tests (2 files)

Location: `core/src/test/kotlin/io/specmatic/stub/stateful/`

| File | What It Tests |
|---|---|
| `StubCacheCRUDPropertyTest.kt` | StubCache add/find/update/delete consistency |
| `StubCacheFindAllPropertyTest.kt` | StubCache findAll with filtering and attribute selection |

### 3.3 Application — Command & Integration Property Tests (8 files)

Location: `application/src/test/kotlin/application/`

| File | What It Tests |
|---|---|
| `AllBackportedCommandsRegisteredPropertyTest.kt` | All 13 backported commands are registered in `SpecmaticCoreSubcommands` |
| `CliDispatchResolvesBackportedCommandsPropertyTest.kt` | CLI dispatch resolves all backported command names |
| `ContractDifferencePropertyTest.kt` | `DifferenceCommand` correctly identifies API differences |
| `BundleContainsAllContractsPropertyTest.kt` | `BundleCommand` includes all contract files |
| `CentralContractRepoReportPropertyTest.kt` | Report generation covers all specifications |
| `MergePreservesScenariosPropertyTest.kt` | `MergeCommand` preserves all scenarios |
| `RedeclaredAPIDetectionPropertyTest.kt` | Redeclared API detection correctness |
| `InteractiveSubcommandPropertyTest.kt` | `Interactive` subcommand declares all required CLI options |

---

## 4. Files Intentionally Not Copied

| File | Reason |
|---|---|
| `HttpStubFilterContext.kt` | Main uses `ExampleFilterContext` (same logic, renamed) |
| `HttpStubFilterContextTest.kt` | Tests the fork-only class |
| `StubCommand.kt` | Main's version already uses `ExampleFilterContext` |
| `CompatibilityReport.kt` | Already exists identically in Main |
| `CompatibleReport.kt` | Already exists identically in Main |
| `ExampleMismatchMessages.kt` | Already exists identically in Main |
| `FixExampleResponse.kt` | Already exists identically in Main |
| `ScenarioFilter.kt` | Already exists identically in Main |
| `SchemaExample.kt` | Already exists identically in Main |

---

## 5. Verification Results

- `./gradlew compileKotlin`: passes with zero errors
- `./gradlew test`: 5530 of 5532 tests pass; the 2 failures are pre-existing and unrelated to the forward-port
