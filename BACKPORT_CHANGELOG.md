# Backport Changelog: specmatic-2.7.1 → specmatic-2.42.0

This document describes all changes made to the `specmatic-2.42.0` fork as part of two backport operations from `specmatic-2.7.1`. The fork retains the 2.42.0 core features (anyOf JSON schema support, Kotlin 2.3.0, pure picocli architecture) while restoring functionality removed between 2.7.1 and 2.42.0.

Two specs drove the work:

1. **examples-interactive-backport** — Restored the `examples interactive` subcommand and its Ktor-based web server for interactively generating, validating, fixing, and testing API examples.
2. **specmatic-command-backport** — Restored 13 CLI commands removed between 2.7.1 and 2.42.0, plus supporting core classes (StatefulHttpStub, StubCache, CentralContractRepoReport, Contract).

## API Adaptation Rules

All backported code was adapted from the 2.7.1 Spring Boot + picocli architecture to the 2.42.0 pure picocli + CliConfigurer architecture. Key adaptations applied across the board:

| 2.7.1 Pattern | 2.42.0 Replacement |
|---|---|
| `@Autowired` field injection | Direct instantiation in `call()` |
| `@Component` / `@Configuration` / `@Bean` | Removed entirely |
| `InteractiveExamplesMismatchMessages` | `ExampleMismatchMessages` (renamed object, same interface) |
| `ExamplesInteractiveServer.ScenarioFilter` (inner class) | Standalone `io.specmatic.core.examples.server.ScenarioFilter` |
| `Feature.stubsFromExamples` | `Feature.inlineNamedStubs` (renamed property) |
| `contractFilePathsFrom(configPath, workDir) { ... }` | `contractFilePathsFrom(configPath, workDir, useCurrentBranch) { ... }` (added parameter) |
| `@Command(subcommands = [...])` class-ref registration | `SpecmaticCoreSubcommands.subcommands()` instance array |

---

## 1. Files Modified in specmatic-2.42.0

These files already existed in the 2.42.0 codebase and were updated to support the backported functionality.

### 1.1 `application/src/main/kotlin/application/SpecmaticCommand.kt`

**Change**: Extended `SpecmaticCoreSubcommands.subcommands()` to register 13 backported CLI commands.

Added after the existing `*ReporterSubcommands.subcommands()` entry:

```kotlin
// Backported commands from 2.7.1
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

`CentralContractRepoReportCommand` is intentionally not registered here because `ReporterSubcommands` already provides a command with the same CLI name (`central-contract-repo-report`).

### 1.2 `application/src/main/kotlin/application/ExamplesCommand.kt`

**Change**: Added the `Interactive` subcommand for the interactive examples web server.

- `@Command(subcommands = [...])` annotation updated from `[Validate::class]` to `[Validate::class, Interactive::class]`
- Added `Interactive` inner class implementing `Callable<Unit>` with 8 picocli `@Option` fields:
  - `--contract-file`, `--filter`, `--filter-name`, `--filter-not-name`, `--debug`, `--dictionary`, `--testBaseURL`, `--allow-only-mandatory-keys-in-payload`
- The `Interactive.call()` method starts an `ExamplesInteractiveServer` on `0.0.0.0:9001` and registers a shutdown hook
- Added imports: `ExamplesInteractiveServer`, `StringLog`, `consoleLog`, `consolePrintableURL`, `exceptionCauseMessage`, `exitWithMessage`, `Thread.sleep`

### 1.3 `application/src/main/kotlin/application/SpecmaticConfig.kt`

**Change**: Created the application-level `SpecmaticConfig` class (this file did not exist in the original 2.42.0; the core module has a `SpecmaticConfig` interface but the application module had no local wrapper).

Added:
- `configFilePath` property returning `Configuration.configFilePath`
- `contractStubPaths()` method
- `contractTestPaths()` method
- `contractStubPathData()` method
- `contractTestPathData()` method

All methods delegate to `contractFilePathsFrom()` with the 2.42.0 `useCurrentBranchForCentralRepo` parameter. These are consumed by `BundleCommand`, `VirtualServiceCommand`, and `CentralContractRepoReportCommand`.

### 1.4 `core/build.gradle.kts`

**Change**: Added Kotest property testing dependency:

```kotlin
testImplementation("io.kotest:kotest-property-jvm:6.1.2")
```

### 1.5 `application/build.gradle.kts`

**Change**: Added Kotest property testing dependencies:

```kotlin
testImplementation("io.kotest:kotest-assertions-core-jvm:6.1.2")
testImplementation("io.kotest:kotest-property-jvm:6.1.2")
```

---

## 2. New Files Added to specmatic-2.42.0

### 2.1 Core Module — Interactive Examples Server (22 files)

Location: `core/src/main/kotlin/io/specmatic/core/examples/server/`

These files implement the interactive examples web server that powers the `examples interactive` CLI subcommand. Four files (`ExampleMismatchMessages.kt`, `FixExampleResponse.kt`, `ScenarioFilter.kt`, `SchemaExample.kt`) already existed in 2.42.0 and were not overwritten.


| File | Purpose |
|---|---|
| `CustomJsonNodeFactory.kt` | Custom Jackson node factory for JSON line-number tracking |
| `CustomParserFactory.kt` | Custom Jackson parser factory |
| `ExamplePageRequest.kt` | Request DTO for example page loading |
| `ExamplesInteractiveServer.kt` | Ktor-based interactive server (main entry point) |
| `ExamplesView.kt` | View model for the examples UI, includes `HtmlTemplateConfiguration`, `TableRow`, `Endpoint`, and grouping data classes |
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
| `StubCache.kt` | Thread-safe in-memory cache (`ReentrantLock`) for stub responses with CRUD operations, filtering, and attribute selection. Includes `CachedResponse` data class. |

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
| `FileOperations.kt` | File I/O utility class (Spring `@Component` annotation removed) |
| `Outcome.kt` | Result monad for operation outcomes |
| `PartialCommitFetch.kt` | Git commit content fetcher |
| `Zipper.kt` | ZIP archive creation utility (Spring `@Component` annotation removed) |

---

## 3. Property Tests Added

### 3.1 Core — Examples Server Property Tests (7 files)

Location: `core/src/test/kotlin/io/specmatic/core/examples/server/`

| File | What It Tests |
|---|---|
| `ExamplesServerClassesPropertyTest.kt` | All supporting data classes are loadable from the correct package |
| `NoDeprecatedRefsPropertyTest.kt` | No deprecated `InteractiveExamplesMismatchMessages` references or inner `ScenarioFilter` declarations |
| `ExampleValidationPropertyTest.kt` | Example validation correctness across generated inputs |
| `FixExampleRoundTripPropertyTest.kt` | Fix-example round-trip consistency |
| `UpdateContentRoundTripPropertyTest.kt` | Content update round-trip consistency |
| `GenerateExamplePropertyTest.kt` | Example generation produces valid outputs for matching scenarios |
| `BulkModeGenerationPropertyTest.kt` | Bulk generation mode skips existing examples |

### 3.2 Core — Stub Cache Property Tests (2 files)

Location: `core/src/test/kotlin/io/specmatic/stub/stateful/`

| File | What It Tests |
|---|---|
| `StubCacheCRUDPropertyTest.kt` | StubCache add/find/update/delete consistency |
| `StubCacheFindAllPropertyTest.kt` | StubCache findAll with filtering and attribute selection |

### 3.3 Application — Command & Integration Property Tests (11 files)

Location: `application/src/test/kotlin/application/`

| File | What It Tests |
|---|---|
| `AllBackportedCommandsRegisteredPropertyTest.kt` | All 13 backported commands are registered in `SpecmaticCoreSubcommands` |
| `CliDispatchResolvesBackportedCommandsPropertyTest.kt` | CLI dispatch resolves all backported command names |
| `NoSpringAnnotationsPropertyTest.kt` | No Spring annotations on backported classes (FileOperations, Zipper, commands) |
| `ContractDifferencePropertyTest.kt` | `DifferenceCommand` correctly identifies API differences |
| `BundleContainsAllContractsPropertyTest.kt` | `BundleCommand` includes all contract files |
| `TestBundleYamlCompanionsPropertyTest.kt` | Test bundle includes YAML companion files |
| `CentralContractRepoReportPropertyTest.kt` | Report generation covers all specifications |
| `MergePreservesScenariosPropertyTest.kt` | `MergeCommand` preserves all scenarios |
| `GherkinToOpenApiPropertyTest.kt` | Gherkin to OpenAPI round-trip validity |
| `RedeclaredAPIDetectionPropertyTest.kt` | Redeclared API detection correctness |
| `InteractiveSubcommandPropertyTest.kt` | `Interactive` subcommand declares all required CLI options |

### 3.4 Application — Unit Tests (1 file)

| File | What It Tests |
|---|---|
| `VirtualServiceCommandTest.kt` | VirtualServiceCommand shutdown behavior and error handling |

---

## 4. Files Intentionally Not Copied from 2.7.1

| File | Reason |
|---|---|
| `InteractiveExamplesMismatchMessages.kt` | 2.42.0 uses `ExampleMismatchMessages` (renamed, same interface) |
| `SpecmaticApplicationRunner.kt` | Spring Boot runner, not needed in pure picocli architecture |
| `SpecmaticBeans.kt` | Spring `@Configuration` / `@Bean` factory, replaced by direct instantiation |
| `BackwardCompatibilityCheckCommand.kt` | 2.42.0 has `BackwardCompatibilityCheckCommandV2` (upgraded version) |
| `ScenarioFilter.kt` (inner class in `ExamplesInteractiveServer`) | Already extracted to standalone class in 2.42.0 |
| `SchemaExample.kt` | Already exists identically in 2.42.0 |
| `FixExampleResponse.kt` | Already exists identically in 2.42.0 |
| `ExampleMismatchMessages.kt` | Already exists in 2.42.0 (renamed from `InteractiveExamplesMismatchMessages`) |

---

## 5. Verification Results

- `./gradlew compileKotlin`: passes with zero errors
- `./gradlew test`: all tests pass
