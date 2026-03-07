package io.specmatic.stub.stateful

import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Feature: specmatic-command-backport, Property 4: StubCache CRUD consistency
class StubCacheCRUDPropertyTest {

    private val arbNonEmptyString: Arb<String> = Arb.string(1..20, Codepoint.alphanumeric())

    private fun jsonObject(idKey: String, idValue: String, extraKey: String, extraValue: String): JSONObjectValue {
        return JSONObjectValue(
            mapOf(
                idKey to StringValue(idValue),
                extraKey to StringValue(extraValue)
            )
        )
    }

    @Test
    fun `add then find returns matching body`() {
        runBlocking {
            checkAll(100, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString) {
                path, idKey, idValue, extraKey, extraValue ->

                val cache = StubCache()
                val body = jsonObject(idKey, idValue, extraKey, extraValue)

                cache.addResponse(path, body, idKey, idValue)

                val found = cache.findResponseFor(path, idKey, idValue)
                assertThat(found).isNotNull
                assertThat(found!!.path).isEqualTo(path)
                assertThat(found.responseBody).isEqualTo(body)
            }
        }
    }

    @Test
    fun `delete then find returns null`() {
        runBlocking {
            checkAll(100, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString) {
                path, idKey, idValue, extraKey, extraValue ->

                val cache = StubCache()
                val body = jsonObject(idKey, idValue, extraKey, extraValue)

                cache.addResponse(path, body, idKey, idValue)
                cache.deleteResponse(path, idKey, idValue)

                val found = cache.findResponseFor(path, idKey, idValue)
                assertThat(found).isNull()
            }
        }
    }

    @Test
    fun `update then find returns new body`() {
        runBlocking {
            checkAll(100, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString) {
                path, idKey, idValue, extraKey, originalValue, updatedValue ->

                val cache = StubCache()
                val originalBody = jsonObject(idKey, idValue, extraKey, originalValue)
                val updatedBody = jsonObject(idKey, idValue, extraKey, updatedValue)

                cache.addResponse(path, originalBody, idKey, idValue)
                cache.updateResponse(path, updatedBody, idKey, idValue)

                val found = cache.findResponseFor(path, idKey, idValue)
                assertThat(found).isNotNull
                assertThat(found!!.responseBody).isEqualTo(updatedBody)
            }
        }
    }

    @Test
    fun `add is idempotent - duplicate add does not create second entry`() {
        runBlocking {
            checkAll(100, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString) {
                path, idKey, idValue, extraKey, extraValue ->

                val cache = StubCache()
                val body = jsonObject(idKey, idValue, extraKey, extraValue)

                cache.addResponse(path, body, idKey, idValue)
                cache.addResponse(path, body, idKey, idValue)

                cache.deleteResponse(path, idKey, idValue)
                val found = cache.findResponseFor(path, idKey, idValue)
                assertThat(found).isNull()
            }
        }
    }

    @Test
    fun `find on empty cache returns null`() {
        runBlocking {
            checkAll(100, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString) {
                path, idKey, idValue ->

                val cache = StubCache()
                val found = cache.findResponseFor(path, idKey, idValue)
                assertThat(found).isNull()
            }
        }
    }

    @Test
    fun `entries with different paths are independent`() {
        runBlocking {
            val arbDistinctPaths = arbitrary {
                val a = arbNonEmptyString.bind()
                val b = arbNonEmptyString.bind()
                if (a == b) Pair(a, "${b}_other") else Pair(a, b)
            }

            checkAll(100, arbDistinctPaths, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString, arbNonEmptyString) {
                (pathA, pathB), idKey, idValue, extraKey, extraValue ->

                val cache = StubCache()
                val body = jsonObject(idKey, idValue, extraKey, extraValue)

                cache.addResponse(pathA, body, idKey, idValue)

                val foundOnA = cache.findResponseFor(pathA, idKey, idValue)
                val foundOnB = cache.findResponseFor(pathB, idKey, idValue)

                assertThat(foundOnA).isNotNull
                assertThat(foundOnB).isNull()
            }
        }
    }
}
