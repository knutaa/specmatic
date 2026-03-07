package application

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.specmatic.core.YAML
import io.specmatic.core.utilities.ContractPathData
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

// Feature: specmatic-command-backport, Property 6: Test bundle YAML companions
// **Validates: Requirements 4.3**
class TestBundleYamlCompanionsPropertyTest {

    /**
     * A test-friendly Bundle that mimics TestBundle.ancillaryEntries() logic:
     * checks for YAML companion files (.yaml alongside .spec contracts)
     * and produces ZipperEntry items for them.
     */
    private class TestableTestBundle(
        private val pathDataList: List<ContractPathData>,
        private val fileOperations: FileOperations
    ) : Bundle {
        override val bundlePath: String = "test-bundle.zip"

        override fun contractPathData(): List<ContractPathData> = pathDataList

        override fun ancillaryEntries(pathData: ContractPathData): List<ZipperEntry> {
            val base = File(pathData.baseDir)
            val yamlPath = pathData.path.removeSuffix(".spec") + ".$YAML"

            return if (File(yamlPath).exists()) {
                val yamlFilePath = File(yamlPath)
                val yamlRelativePath = yamlFilePath.relativeTo(base).path
                val yamlEntryName = "${base.name}${File.separator}$yamlRelativePath"
                val yamlEntry = ZipperEntry(yamlEntryName, fileOperations.readBytes(yamlFilePath.path))
                listOf(yamlEntry)
            } else {
                emptyList()
            }
        }

        override fun configEntry(): List<ZipperEntry> = emptyList()
    }

    // Generator for safe file name segments (alphanumeric, 3-8 chars)
    private val arbFileNameSegment: Arb<String> = Arb.string(3..8, Codepoint.alphanumeric())
        .filter { it.isNotBlank() && it[0].isLetter() }

    // Generator for number of contracts (1-4)
    private val arbContractCount: Arb<Int> = Arb.int(1..4)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `contracts with YAML companions produce ZipperEntry for the YAML file`() {
        runBlocking {
            checkAll(100, arbContractCount, arbFileNameSegment) { contractCount, prefix ->
                val baseDir = tempDir.resolve("yaml_with_${prefix}_${System.nanoTime()}").toFile()
                baseDir.mkdirs()

                // All contracts in this test have YAML companions
                val contractFiles = (1..contractCount).map { i ->
                    val contractFile = File(baseDir, "${prefix}_contract_$i.spec")
                    contractFile.writeText("Feature: Test $i")

                    val yamlFile = File(baseDir, "${prefix}_contract_$i.$YAML")
                    yamlFile.writeText("openapi: 3.0.3\ninfo:\n  title: Test $i\n  version: '1.0'\npaths: {}")

                    contractFile
                }

                val pathDataList = contractFiles.map { ContractPathData(baseDir.absolutePath, it.absolutePath) }
                val fileOperations = FileOperations()
                val bundle = TestableTestBundle(pathDataList, fileOperations)

                val zipperEntries = pathDataList.flatMap { pathData ->
                    pathDataToZipperEntry(bundle, pathData, fileOperations)
                }

                val entryPaths = zipperEntries.map { it.path }

                // Each contract with a YAML companion should have a YAML entry
                for (contractFile in contractFiles) {
                    val yamlFile = File(contractFile.path.removeSuffix(".spec") + ".$YAML")
                    val yamlRelativePath = yamlFile.relativeTo(baseDir).path
                    val expectedYamlEntry = "${baseDir.name}${File.separator}$yamlRelativePath"

                    assertThat(entryPaths)
                        .describedAs("ZipperEntry list should contain YAML companion for: ${contractFile.name}")
                        .contains(expectedYamlEntry)
                }
            }
        }
    }

    @Test
    fun `contracts without YAML companions do NOT produce extra entries`() {
        runBlocking {
            checkAll(100, arbContractCount, arbFileNameSegment) { contractCount, prefix ->
                val baseDir = tempDir.resolve("yaml_without_${prefix}_${System.nanoTime()}").toFile()
                baseDir.mkdirs()

                // No YAML companions created
                val contractFiles = (1..contractCount).map { i ->
                    val contractFile = File(baseDir, "${prefix}_solo_$i.spec")
                    contractFile.writeText("Feature: Test $i")
                    contractFile
                }

                val pathDataList = contractFiles.map { ContractPathData(baseDir.absolutePath, it.absolutePath) }
                val fileOperations = FileOperations()
                val bundle = TestableTestBundle(pathDataList, fileOperations)

                val zipperEntries = pathDataList.flatMap { pathData ->
                    pathDataToZipperEntry(bundle, pathData, fileOperations)
                }

                // Only contract entries should exist, no YAML entries
                assertThat(zipperEntries.size)
                    .describedAs("Only contract entries should exist when no YAML companions are present")
                    .isEqualTo(contractCount)

                // Verify no .yaml entries
                val yamlEntries = zipperEntries.filter { it.path.endsWith(".$YAML") }
                assertThat(yamlEntries)
                    .describedAs("No YAML entries should be present when companions don't exist")
                    .isEmpty()
            }
        }
    }

    @Test
    fun `mixed contracts - only those with YAML companions get YAML entries`() {
        runBlocking {
            checkAll(100, arbContractCount, arbFileNameSegment) { contractCount, prefix ->
                // Ensure at least 2 contracts so we can have a mix
                val totalContracts = contractCount + 1
                val baseDir = tempDir.resolve("yaml_mixed_${prefix}_${System.nanoTime()}").toFile()
                baseDir.mkdirs()

                val contractsWithYaml = mutableListOf<File>()
                val contractsWithoutYaml = mutableListOf<File>()

                val contractFiles = (1..totalContracts).map { i ->
                    val contractFile = File(baseDir, "${prefix}_mix_$i.spec")
                    contractFile.writeText("Feature: Test $i")

                    // Even-indexed contracts get YAML companions, odd ones don't
                    if (i % 2 == 0) {
                        val yamlFile = File(baseDir, "${prefix}_mix_$i.$YAML")
                        yamlFile.writeText("openapi: 3.0.3\ninfo:\n  title: Test $i\n  version: '1.0'\npaths: {}")
                        contractsWithYaml.add(contractFile)
                    } else {
                        contractsWithoutYaml.add(contractFile)
                    }

                    contractFile
                }

                val pathDataList = contractFiles.map { ContractPathData(baseDir.absolutePath, it.absolutePath) }
                val fileOperations = FileOperations()
                val bundle = TestableTestBundle(pathDataList, fileOperations)

                val zipperEntries = pathDataList.flatMap { pathData ->
                    pathDataToZipperEntry(bundle, pathData, fileOperations)
                }

                val entryPaths = zipperEntries.map { it.path }

                // Contracts WITH YAML companions should have YAML entries
                for (contractFile in contractsWithYaml) {
                    val yamlFile = File(contractFile.path.removeSuffix(".spec") + ".$YAML")
                    val yamlRelativePath = yamlFile.relativeTo(baseDir).path
                    val expectedYamlEntry = "${baseDir.name}${File.separator}$yamlRelativePath"

                    assertThat(entryPaths)
                        .describedAs("Should contain YAML entry for: ${contractFile.name}")
                        .contains(expectedYamlEntry)
                }

                // Contracts WITHOUT YAML companions should NOT have YAML entries
                for (contractFile in contractsWithoutYaml) {
                    val yamlFile = File(contractFile.path.removeSuffix(".spec") + ".$YAML")
                    val yamlRelativePath = yamlFile.relativeTo(baseDir).path
                    val expectedYamlEntry = "${baseDir.name}${File.separator}$yamlRelativePath"

                    assertThat(entryPaths)
                        .describedAs("Should NOT contain YAML entry for: ${contractFile.name}")
                        .doesNotContain(expectedYamlEntry)
                }

                // Total entries = all contracts + contracts with YAML companions
                val expectedTotal = totalContracts + contractsWithYaml.size
                assertThat(zipperEntries.size)
                    .describedAs("Total entries should be $totalContracts contracts + ${contractsWithYaml.size} YAML companions")
                    .isEqualTo(expectedTotal)
            }
        }
    }
}
