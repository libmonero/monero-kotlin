package monero.internal

import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import monero.NativeTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag

@Tag("unit")
class MoneroNativeAbiTest {

    @BeforeEach
    fun requireLibrary() {
        NativeTestSupport.assumeAvailable()
    }

    @Test
    fun theLibrary_IsOfTheAbiThatTheCodeNeeds() {
        val major = IntByReference()
        val minor = IntByReference()
        val patch = IntByReference()
        MoneroNative.ok(MoneroNative.lib.monero_utils_get_abi_version(major, minor, patch))

        assertEquals(MoneroNative.ABI_MAJOR, major.value)
        assertEquals(MoneroNative.ABI_MINOR, minor.value)
    }

    @Test
    fun aDaemonListener_IsCreatedWithTheSizeOfItsCallbacks() {
        val callbacks = DaemonListenerCallbacks()
        callbacks.structSize = callbacks.size()

        // the struct starts with its size, then the user data and the one callback: 4 bytes, 4 of padding, 8 and 8
        assertEquals(24, callbacks.size())

        DaemonEvents { }.use { events -> assertNotNull(events.handle) }
    }

    @Test
    fun aDaemonListener_WithoutTheSizeOfItsCallbacks_IsRefused() {
        val callbacks = DaemonListenerCallbacks()
        val out = PointerByReference()

        val result = MoneroNative.lib.monero_daemon_listener_create(callbacks, out)

        assertNotEquals(0, result)
    }
}
