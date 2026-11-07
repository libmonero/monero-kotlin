package monero

import java.lang.reflect.Method
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.platform.commons.support.AnnotationSupport
import org.opentest4j.AssertionFailedError
import org.opentest4j.TestAbortedException

/**
 * Gives [NotSupported] and [NotImplemented] their meaning, as the conftest.py of monero-python does for its marks. It is on for
 * every test, as [TestLogExtension] is: META-INF/services/org.junit.jupiter.api.extension.Extension names it.
 */
class TestMarkersExtension : InvocationInterceptor {

    override fun interceptTestMethod(invocation: InvocationInterceptor.Invocation<Void>, invocationContext: ReflectiveInvocationContext<Method>, extensionContext: ExtensionContext) {
        intercept(invocation, extensionContext)
    }

    override fun interceptTestTemplateMethod(invocation: InvocationInterceptor.Invocation<Void>, invocationContext: ReflectiveInvocationContext<Method>, extensionContext: ExtensionContext) {
        intercept(invocation, extensionContext)
    }

    private fun intercept(invocation: InvocationInterceptor.Invocation<Void>, context: ExtensionContext) {
        val name = "${context.requiredTestClass.simpleName}.${context.requiredTestMethod.name}"
        when {
            isMarked(context, NotSupported::class.java) -> TestMarkers.expectNotSupported(name) { invocation.proceed() }
            isMarked(context, NotImplemented::class.java) -> TestMarkers.expectNotImplemented(name) { invocation.proceed() }
            else -> invocation.proceed()
        }
    }

    private fun isMarked(context: ExtensionContext, mark: Class<out Annotation>): Boolean {
        return AnnotationSupport.isAnnotated(context.requiredTestMethod, mark) || AnnotationSupport.isAnnotated(context.requiredTestClass, mark)
    }
}

/**
 * What the marks do to a test, apart from JUnit so that it can be tested.
 */
object TestMarkers {

    // the words that monero-python looks for in the error of a test that is not supported
    private val NOT_SUPPORTED_HINTS = listOf("not supported", "does not support", "doesn't support")

    /**
     * Run a test that has to throw an error that says that the operation is not supported: that is a pass. Any other error is
     * the error of the test, and no error fails it. An assumption that is not met goes through, as it skips the test.
     */
    fun expectNotSupported(name: String, test: () -> Unit) {
        try {
            test()
        } catch (e: TestAbortedException) {
            throw e
        } catch (e: Exception) {
            val message = e.toString().lowercase()
            if (NOT_SUPPORTED_HINTS.none { message.contains(it) }) throw e
            TestLog.logger("TestMarkers").fine("NOT SUPPORTED (as expected) $name: $e")
            return
        }
        throw AssertionFailedError("Expected a 'not supported' error")
    }

    /**
     * Run a test of an operation that is not implemented yet: while it fails the test is skipped, with the failure as the reason,
     * and once it passes it passes and the log says to remove the mark. An assumption that is not met goes through as it is.
     */
    fun expectNotImplemented(name: String, test: () -> Unit) {
        try {
            test()
        } catch (e: TestAbortedException) {
            throw e
        } catch (e: Throwable) {
            // an error of the JVM is not a failure of the test
            if (e !is Exception && e !is AssertionError) throw e
            TestLog.logger("TestMarkers").info("NOT IMPLEMENTED (as expected) $name: $e")
            throw TestAbortedException("Not implemented: $e", e)
        }
        TestLog.logger("TestMarkers").warning("$name passes: remove @NotImplemented")
    }
}
