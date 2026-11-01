package monero.internal

import com.sun.jna.ptr.IntByReference
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import monero.common.MoneroError

/**
 * Tests the comparison of the version of the ABI of the library with the one that this code was written for. The
 * library is a fake that answers the version numbers of the test: no native library is loaded.
 */
class MoneroNativeAbiTest {

    // The version that this code was written for is accepted, whatever its patch
    @Test
    fun testAcceptsTheWrittenVersion() {
        MoneroNative.checkAbi(libraryWithVersion(0, 3, 0), "test-library")
        MoneroNative.checkAbi(libraryWithVersion(0, 3, 9), "test-library")
    }

    // While the major version is 0, a change of the minor version can break callers, so a smaller one and a greater
    // one are both refused (monero_c_utils.h:155-158)
    @Test
    fun testRefusesTheOtherMinors() {
        val tooOld = assertFailsWith<MoneroError> { MoneroNative.checkAbi(libraryWithVersion(0, 2, 9), "test-library") }
        assertTrue("0.2.9" in tooOld.message!!, "the message names the version of the library: ${tooOld.message}")
        assertTrue("the minor version 3" in tooOld.message!!, "the message names the minor version that is needed: ${tooOld.message}")

        val tooNew = assertFailsWith<MoneroError> { MoneroNative.checkAbi(libraryWithVersion(0, 4, 0), "test-library") }
        assertTrue("0.4.0" in tooNew.message!!, "the message names the version of the library: ${tooNew.message}")
    }

    // Only the major version that this code was written for is accepted, and the message names the library
    @Test
    fun testRefusesTheOtherMajors() {
        val error = assertFailsWith<MoneroError> { MoneroNative.checkAbi(libraryWithVersion(1, 0, 0), "the-loaded-path") }
        assertTrue("1.0.0" in error.message!!, "the message names the version of the library: ${error.message}")
        assertTrue("the-loaded-path" in error.message!!, "the message names the path of the library: ${error.message}")
    }

    // A library that has no version of its ABI is too old for the check
    @Test
    fun testRefusesTheLibraryWithoutAVersion() {
        val library = Proxy.newProxyInstance(MoneroC::class.java.classLoader, arrayOf(MoneroC::class.java)) { _, method, _ ->
            if (method.name == "monero_utils_get_abi_version") throw UnsatisfiedLinkError("no such symbol")
            0
        } as MoneroC
        val error = assertFailsWith<MoneroError> { MoneroNative.checkAbi(library, "test-library") }
        assertTrue("too old" in error.message!!, "the message says that the library is too old: ${error.message}")
    }

    // ------------------------------- PRIVATE ------------------------------------

    /**
     * Make a fake of the functions of monero-c that answers the given version of the ABI.
     *
     * @param major is the major version to answer
     * @param minor is the minor version to answer
     * @param patch is the patch version to answer
     * @return the fake library
     */
    private fun libraryWithVersion(major: Int, minor: Int, patch: Int): MoneroC {
        return Proxy.newProxyInstance(MoneroC::class.java.classLoader, arrayOf(MoneroC::class.java)) { _, method, args ->
            if (method.name == "monero_utils_get_abi_version") {
                (args!![0] as IntByReference).value = major
                (args[1] as IntByReference).value = minor
                (args[2] as IntByReference).value = patch
            }
            0
        } as MoneroC
    }
}
