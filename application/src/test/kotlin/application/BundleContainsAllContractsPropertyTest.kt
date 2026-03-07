package application

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.specmatic.core.utilities.ContractPathData
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

// Feature: specmatic-command-backport, Property 5: Bundle contains all configured contracts
// **Validates: Requirements 4.1**
class BundleContainsAllContractsPropertyTest {

    /**
     * A minimal Bundle implementation for testing that uses pre-built ContractPathData
     * and delegates ancillary entry generation to the real StubBundle logic
     * (via the top-level pathDataToZipperEntry function which calls bundle.ancillaryEntries).
     *
     * We create a simple stub bundle that reads stub JSON files from the conventional
     * _data directory next to each contract file.
     */
    private class TestableStubBundle(
        private val pathDataList: List<ContractPathData>,
        private val fileOperations: FileOperations
    ) : Bundle {
        override val bundlePath: String = "test-bundle.zip"

        override fun contractPathData(): List<ContractPathData> = pathDataList

        override fun ancillaryEntries(pathData: ContractPathData): List<ZipperEntry> {
            val base = File(pathData.baseDir)
            val stubDataDir = stubDataDirRelative(File(pathData.path))
            val stubFiles = stubFilesIn(stubDataDir, fileOperations)

            return stubFiles.map {
                val relativeEntryPath = File(it).relativeTo(base)
                ZipperEntry(
                    "${base.name}${File.separator}${relativeEntryPath.path}",
                    fileOperations.readBytes(it)
                )
            }
        }

        override fun configEntry(): List<ZipperEntry> = emptyList()
    }

    // Generator for safe file name segments (alphanumeric, 3-8 chars)
    private val arbFileNameSegment: Arb<String> = Arb.string(3..8, Codepoint.alphanumeric())
        .filter { it.isNotBlank() && it[0].isLetter() }

    // Generator for number of contracts (1-3)
    private val arbContractCount: Arb<Int> = Arb.int(1..3)

    // Generator for number of stub JSON files per contract (0-3)
    private val arbStubFileCount: Arb<Int> = Arb.int(0..3)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `ZipperEntry list contains an entry for each contract file`() {
        runBlocking {
            checkAll(100, arbContractCount, arbFileNameSegment, arbFileNameSegment) { contractCount, baseName, prefix ->
                val baseDir = tempDir.resolve("base_${baseName}_${System.nanoTime()}").toFile()
                baseDir.mkdirs()

                val contractFiles = (1..contractCount).map { i ->
                    val contractFile = File(baseDir, "${prefix}_contract_$i.yaml")
                    contractFile.writeText("openapi: 3.0.3\ninfo:\n  title: Test $i\n  version: '1.0'\npaths: {}")
                    // Create empty _data directory (no stubs)
                    File(baseDir, "${prefix}_contract_${i}_data").mkdirs()
                    contractFile
                }

                val pathDataList = contractFiles.map { ContractPathData(baseDir.absolutePath, it.absolutePath) }
                val fileOperations = FileOperations()
                val bundle = TestableStubBundle(pathDataList, fileOperations)

                val zipperEntries = pathDataList.flatMap { pathData ->
                    pathDataToZipperEntry(bundle, pathData, fileOperations)
                }

                val entryPaths = zipperEntries.map { it.path }

                // Each contract file should have a corresponding entry
                for (contractFile in contractFiles) {
                    val relativePath = contractFile.relativeTo(baseDir).path
                    val expectedEntryPath = "${baseDir.name}${File.separator}$relativePath"
                    assertThat(entryPaths)
                        .describedAs("ZipperEntry list should contain entry for contract file: $relativePath")
                        .contains(expectedEntryPath)
                }
            }
        }
    }

    @Test
    fun `ZipperEntry list contains an entry for each stub JSON file`() {
        runBlocking {
            checkAll(100, arbContractCount, arbStubFileCount, arbFileNameSegment) { contractCount, stubCount, prefix ->
                val baseDir = tempDir.resolve("base_${prefix}_${System.nanoTime()}").toFile()
                baseDir.mkdirs()

                val allStubFiles = mutableListOf<Pair<File, File>>() // (contractFile, stubFile) pairs

                val contractFiles = (1..contractCount).map { i ->
                    val contractFile = File(baseDir, "${prefix}_api_$i.yaml")
                    contractFile.writeText("openapi: 3.0.3\ninfo:\n  title: Test $i\n  version: '1.0'\npaths: {}")

                    val stubDataDir = File(baseDir, "${prefix}_api_${i}_data")
                    stubDataDir.mkdirs()

                    // Create stub JSON files
                    (1..stubCount).forEach { j ->
                        val stubFile = File(stubDataDir, "stub_$j.json")
                        stubFile.writeText("""{"request": {}, "response": {"status": 200}}""")
                        allStubFiles.add(Pair(contractFile, stubFile))
                    }

                    contractFile
                }

                val pathDataList = contractFiles.map { ContractPathData(baseDir.absolutePath, it.absolutePath) }
                val fileOperations = FileOperations()
                val bundle = TestableStubBundle(pathDataList, fileOperations)

                val zipperEntries = pathDataList.flatMap { pathData ->
                    pathDataToZipperEntry(bundle, pathData, fileOperations)
                }

                val entryPaths = zipperEntries.map { it.path }

                // Each stub JSON file should have a corresponding entry
                for ((_, stubFile) in allStubFiles) {
                    val relativePath = stubFile.relativeTo(baseDir).path
                    val expectedEntryPath = "${baseDir.name}${File.separator}$relativePath"
                    assertThat(entryPaths)
                        .describedAs("ZipperEntry list should contain entry for stub file: $relativePath")
                        .contains(expectedEntryPath)
                }
            }
        }
    }

    @Test
    fun `total ZipperEntry count equals contract count plus stub file count`() {
        runBlocking {
            checkAll(100, arbContractCount, arbStubFileCount, arbFileNameSegment) { contractCount, stubCount, prefix ->
                val baseDir = tempDir.resolve("base_${prefix}_${System.nanoTime()}").toFile()
                baseDir.mkdirs()

                val contractFiles = (1..contractCount).map { i ->
                    val contractFile = File(baseDir, "${prefix}_spec_$i.yaml")
                    contractFile.writeText("openapi: 3.0.3\ninfo:\n  title: Test $i\n  version: '1.0'\npaths: {}")

                    val stubDataDir = File(baseDir, "${prefix}_spec_${i}_data")
                    stubDataDir.mkdirs()

                    (1..stubCount).forEach { j ->
                        File(stubDataDir, "example_$j.json").writeText("""{"id": $j}""")
                    }

                    contractFile
                }

                val pathDataList = contractFiles.map { ContractPathData(baseDir.absolutePath, it.absolutePath) }
                val fileOperations = FileOperations()
                val bundle = TestableStubBundle(pathDataList, fileOperations)

                val zipperEntries = pathDataList.flatMap { pathData ->
                    pathDataToZipperEntry(bundle, pathData, fileOperations)
                }

                val expectedTotal = contractCount + (contractCount * stubCount)
                assertThat(zipperEntries.size)
                    .describedAs(
                        "Total entries should be $contractCount contracts + ${contractCount * stubCount} stubs = $expectedTotal"
                    )
                    .isEqualTo(expectedTotal)
            }
        }
    }
}
