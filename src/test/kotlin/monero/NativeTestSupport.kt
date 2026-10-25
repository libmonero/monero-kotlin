package monero

import monero.common.MoneroError
import monero.internal.MoneroNative
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * What the tests that call monero-c share. The unit tests of the models need no library, so a test that calls monero-c is
 * skipped when the library is not found, and unit-tests.yml runs it with the library that native.yml built. To run it, set
 * the system property monero.c.library or the environment variable MONERO_C_LIBRARY to the path of the library.
 */
object NativeTestSupport {

    val isAvailable: Boolean by lazy {
        try {
            MoneroNative.lib
            true
        } catch (e: MoneroError) {
            false
        }
    }

    /**
     * Skip the test if monero-c can not be loaded.
     */
    fun assumeAvailable() {
        assumeTrue(isAvailable, "monero-c is not found: set ${MoneroNative.LIBRARY_ENVIRONMENT} to run the tests that call it")
    }
}
