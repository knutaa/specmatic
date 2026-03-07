package application

import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// Feature: specmatic-command-backport, Property 3: No Spring annotations on backported classes
// **Validates: Requirements 1.4, 2.1, 2.2, 2.3**
class NoSpringAnnotationsPropertyTest {

    private val backportedClasses: List<Class<*>> = listOf(
        FileOperations::class.java,
        Zipper::class.java,
    )

    @Test
    fun `no class-level Spring annotations on backported classes`() {
        runBlocking {
            checkAll(Exhaustive.collection(backportedClasses)) { clazz ->
                val springAnnotations = clazz.annotations.filter {
                    it.annotationClass.java.name.startsWith("org.springframework")
                }
                assertThat(springAnnotations)
                    .describedAs("${clazz.simpleName} should have no Spring annotations, but found: $springAnnotations")
                    .isEmpty()
            }
        }
    }

    @Test
    fun `no @Autowired fields on backported classes`() {
        runBlocking {
            checkAll(Exhaustive.collection(backportedClasses)) { clazz ->
                val autowiredFields = clazz.declaredFields.filter { field ->
                    field.annotations.any { it.annotationClass.java.name.startsWith("org.springframework") }
                }
                assertThat(autowiredFields)
                    .describedAs("${clazz.simpleName} should have no @Autowired fields, but found: ${autowiredFields.map { it.name }}")
                    .isEmpty()
            }
        }
    }

    @Test
    fun `no @Bean methods on backported classes`() {
        runBlocking {
            checkAll(Exhaustive.collection(backportedClasses)) { clazz ->
                val beanMethods = clazz.declaredMethods.filter { method ->
                    method.annotations.any { it.annotationClass.java.name.startsWith("org.springframework") }
                }
                assertThat(beanMethods)
                    .describedAs("${clazz.simpleName} should have no @Bean methods, but found: ${beanMethods.map { it.name }}")
                    .isEmpty()
            }
        }
    }
}
