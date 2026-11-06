package monero

import java.util.logging.Level
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher

/**
 * Logs every test class and every test with [TestLog], as the fixtures of the tests of monero-python do, so a run shows what it ran and
 * how it ended. It is on for every test: junit-platform.properties turns on the extensions that the classpath names, and
 * META-INF/services/org.junit.jupiter.api.extension.Extension names this one.
 */
class TestLogExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback, TestWatcher {

    override fun beforeAll(context: ExtensionContext) {
        log(context).info("Run test class ${context.requiredTestClass.simpleName}")
    }

    override fun afterAll(context: ExtensionContext) {
        log(context).info("Teardown test class ${context.requiredTestClass.simpleName}")
    }

    override fun beforeEach(context: ExtensionContext) {
        context.getStore(NAMESPACE).put(START, System.nanoTime())
        log(context).info("Before ${context.requiredTestMethod.name}")
    }

    override fun testSuccessful(context: ExtensionContext) {
        log(context).info("After ${context.requiredTestMethod.name}: passed${took(context)}")
    }

    override fun testFailed(context: ExtensionContext, cause: Throwable) {
        log(context).log(Level.SEVERE, "After ${context.requiredTestMethod.name}: FAILED${took(context)}: $cause")
    }

    // an assumption that is not met, e.g. no library of monero-c or no node
    override fun testAborted(context: ExtensionContext, cause: Throwable?) {
        log(context).info("After ${context.requiredTestMethod.name}: skipped${took(context)}: ${cause?.message}")
    }

    override fun testDisabled(context: ExtensionContext, reason: java.util.Optional<String>) {
        log(context).info("${context.requiredTestMethod.name}: disabled: ${reason.orElse("")}")
    }

    private fun log(context: ExtensionContext) = TestLog.logger(context.requiredTestClass.simpleName)

    private fun took(context: ExtensionContext): String {
        val start = context.getStore(NAMESPACE).get(START, Long::class.javaObjectType) ?: return ""
        return " in ${(System.nanoTime() - start) / 1_000_000} ms"
    }

    private companion object {
        val NAMESPACE: ExtensionContext.Namespace = ExtensionContext.Namespace.create(TestLogExtension::class.java)
        const val START = "start"
    }
}
