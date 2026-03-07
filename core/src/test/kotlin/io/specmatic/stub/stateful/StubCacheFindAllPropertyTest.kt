package io.specmatic.stub.stateful

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Feature: specmatic-command-backport, Property 12: StubCache findAll with attribute selection
class StubCacheFindAllPropertyTest {

    private val arbKey: Arb<String> = Arb.string(1..10, Codepoint.alphanumeric())
    private val arbValue: Arb<String> = Arb.string(1..10, Codepoint.alphanumeric())

    private fun jsonObject(vararg pairs: Pair<String, String>): JSONObjectValue {
        return JSONObjectValue(pairs.associate { (k, v) -> k to StringValue(v) })
    }

    @Test
    fun `findAll returns only responses matching the given path`() {
        runBlocking {
            val arbDistinctPaths = arbitrary {
                val a = arbKey.bind()
                val b = arbKey.bind()
                if (a == b) Pair(a, "${b}_other") else Pair(a, b)
            }

            checkAll(100, arbDistinctPaths, arbKey, arbKey, arbValue, arbValue) {
                (targetPath, otherPath), idKey, extraKey, idValue, extraValue ->

                val cache = StubCache()
                val body1 = jsonObject(idKey to "1", extraKey to extraValue)
                val body2 = jsonObject(idKey to "2", extraKey to extraValue)

                cache.addResponse(targetPath, body1, idKey, "1")
                cache.addResponse(otherPath, body2, idKey, "2")

                val result = cache.findAllResponsesFor(targetPath, emptySet())
                assertThat(result.list).hasSize(1)
                assertThat((result.list[0] as JSONObjectValue).jsonObject[idKey]?.toStringLiteral()).isEqualTo("1")
            }
        }
    }

    @Test
    fun `findAll returns only responses matching the filter`() {
        runBlocking {
            checkAll(100, arbKey, arbKey, arbKey, arbValue, arbValue) {
                path, idKey, filterKey, matchValue, nonMatchValue ->

                val actualNonMatch = if (matchValue == nonMatchValue) "${nonMatchValue}_x" else nonMatchValue

                val cache = StubCache()
                val matchingBody = jsonObject(idKey to "1", filterKey to matchValue)
                val nonMatchingBody = jsonObject(idKey to "2", filterKey to actualNonMatch)

                cache.addResponse(path, matchingBody, idKey, "1")
                cache.addResponse(path, nonMatchingBody, idKey, "2")

                val result = cache.findAllResponsesFor(
                    path,
                    emptySet(),
                    mapOf(filterKey to matchValue)
                )

                assertThat(result.list).hasSize(1)
                val returned = result.list[0] as JSONObjectValue
                assertThat(returned.jsonObject[filterKey]?.toStringLiteral()).isEqualTo(matchValue)
            }
        }
    }

    @Test
    fun `findAll with attribute selection returns only selected keys`() {
        runBlocking {
            checkAll(100, arbKey, arbKey, arbKey, arbKey, arbValue, arbValue, arbValue) {
                path, idKey, selectedKey, excludedKey, idValue, selectedValue, excludedValue ->

                val actualExcludedKey = if (selectedKey == excludedKey || idKey == excludedKey) "${excludedKey}_ex" else excludedKey
                val actualSelectedKey = if (selectedKey == idKey) "${selectedKey}_sel" else selectedKey

                val cache = StubCache()
                val body = jsonObject(
                    idKey to idValue,
                    actualSelectedKey to selectedValue,
                    actualExcludedKey to excludedValue
                )

                cache.addResponse(path, body, idKey, idValue)

                val selectionKeys = setOf(idKey, actualSelectedKey)
                val result = cache.findAllResponsesFor(path, selectionKeys)

                assertThat(result.list).hasSize(1)
                val returned = result.list[0] as JSONObjectValue
                assertThat(returned.jsonObject.keys).containsExactlyInAnyOrderElementsOf(selectionKeys)
                assertThat(returned.jsonObject[idKey]?.toStringLiteral()).isEqualTo(idValue)
                assertThat(returned.jsonObject[actualSelectedKey]?.toStringLiteral()).isEqualTo(selectedValue)
                assertThat(returned.jsonObject).doesNotContainKey(actualExcludedKey)
            }
        }
    }

    @Test
    fun `findAll with filter and attribute selection combined`() {
        runBlocking {
            checkAll(100, arbKey, arbKey, arbKey, arbValue, arbValue) {
                path, idKey, filterKey, matchValue, nonMatchValue ->

                val actualFilterKey = if (filterKey == idKey) "${filterKey}_f" else filterKey
                val actualNonMatch = if (matchValue == nonMatchValue) "${nonMatchValue}_x" else nonMatchValue

                val cache = StubCache()
                val matching = jsonObject(idKey to "1", actualFilterKey to matchValue)
                val nonMatching = jsonObject(idKey to "2", actualFilterKey to actualNonMatch)

                cache.addResponse(path, matching, idKey, "1")
                cache.addResponse(path, nonMatching, idKey, "2")

                val selectionKeys = setOf(idKey)
                val result = cache.findAllResponsesFor(
                    path,
                    selectionKeys,
                    mapOf(actualFilterKey to matchValue)
                )

                assertThat(result.list).hasSize(1)
                val returned = result.list[0] as JSONObjectValue
                assertThat(returned.jsonObject.keys).containsExactlyInAnyOrderElementsOf(selectionKeys)
                assertThat(returned.jsonObject[idKey]?.toStringLiteral()).isEqualTo("1")
            }
        }
    }

    @Test
    fun `findAll with empty selection keys returns full response bodies`() {
        runBlocking {
            checkAll(100, arbKey, arbKey, arbKey, arbValue, arbValue) {
                path, idKey, extraKey, idValue, extraValue ->

                val actualExtraKey = if (extraKey == idKey) "${extraKey}_e" else extraKey

                val cache = StubCache()
                val body = jsonObject(idKey to idValue, actualExtraKey to extraValue)

                cache.addResponse(path, body, idKey, idValue)

                val result = cache.findAllResponsesFor(path, emptySet())

                assertThat(result.list).hasSize(1)
                val returned = result.list[0] as JSONObjectValue
                assertThat(returned.jsonObject.keys).containsExactlyInAnyOrderElementsOf(setOf(idKey, actualExtraKey))
            }
        }
    }

    @Test
    fun `findAll on empty cache returns empty array`() {
        runBlocking {
            checkAll(100, arbKey) { path ->
                val cache = StubCache()
                val result = cache.findAllResponsesFor(path, emptySet())
                assertThat(result.list).isEmpty()
            }
        }
    }

    @Test
    fun `findAll with empty filter returns all responses for path`() {
        runBlocking {
            val arbCount = Arb.int(1..5)

            checkAll(100, arbKey, arbKey, arbCount) { path, idKey, count ->
                val cache = StubCache()

                repeat(count) { i ->
                    val body = jsonObject(idKey to "id_$i")
                    cache.addResponse(path, body, idKey, "id_$i")
                }

                val result = cache.findAllResponsesFor(path, emptySet())
                assertThat(result.list).hasSize(count)
            }
        }
    }
}
