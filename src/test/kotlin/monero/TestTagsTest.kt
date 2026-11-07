package monero

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestTemplate
import org.junit.platform.commons.support.AnnotationSupport

/**
 * Every class of tests is tagged `unit` or `integration`, as the tests of monero-python are marked: the first run without a node, and
 * the second need one and are not run by `test` (build.gradle.kts). A class without a tag would run with the unit tests, whatever it needs.
 */
@Tag("unit")
class TestTagsTest {

    @Test
    fun everyClassOfTests_IsTaggedUnitOrIntegration() {
        val root = File(TestTagsTest::class.java.protectionDomain.codeSource.location.toURI())
        val classes = root.walkTopDown().filter { it.isFile && it.name.endsWith(".class") && '$' !in it.name }
            .map { Class.forName(it.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.')) }
            .filter { type -> type.declaredMethods.any { isTest(it) } }
            .toList()
        assertTrue(classes.size > 10, "The classes of tests are not found in $root")

        for (type in classes) {
            val tags = AnnotationSupport.findRepeatableAnnotations(type, Tag::class.java).map { it.value }.filter { it == "unit" || it == "integration" }
            assertEquals(1, tags.size, "${type.name} has to be tagged unit or integration, and only one of them: $tags")
        }
    }

    private fun isTest(method: java.lang.reflect.Method): Boolean {
        return AnnotationSupport.isAnnotated(method, org.junit.jupiter.api.Test::class.java) ||
            AnnotationSupport.isAnnotated(method, TestFactory::class.java) ||
            AnnotationSupport.isAnnotated(method, TestTemplate::class.java)
    }
}
