package monero

import com.sun.jna.NativeLibrary
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
     * Whether the library of monero-c has the light wallet surface. Not every build of it has the four functions of the
     * light wallet: the submodule of this repository pins a monero-c without them, so a test that calls them skips until
     * the pin moves to a monero-c that has them.
     */
    val isLightWalletAvailable: Boolean by lazy {
        if (!isAvailable) false
        else try {
            val path = System.getProperty(MoneroNative.LIBRARY_PROPERTY) ?: System.getenv(MoneroNative.LIBRARY_ENVIRONMENT) ?: "monero_c"
            NativeLibrary.getInstance(path).getFunction("monero_wallet_light_create")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    /**
     * Skip the test if monero-c can not be loaded.
     */
    fun assumeAvailable() {
        assumeTrue(isAvailable, "monero-c is not found: set ${MoneroNative.LIBRARY_ENVIRONMENT} to run the tests that call it")
    }

    /**
     * Skip the test if the library of monero-c has no light wallet surface.
     */
    fun assumeLightWalletAvailable() {
        assumeTrue(isLightWalletAvailable, "the library of monero-c has no light wallet: the tests that call it need a monero-c with the light wallet surface")
    }
}
