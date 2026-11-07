package monero.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import monero.NativeTestSupport
import org.junit.jupiter.api.Tag

/**
 * The error of a daemon or a wallet RPC server that answers with an error, like MoneroRpcError of monero-python. What makes monero-c
 * report one is in the integration tests of the connection and the daemon, which have a server.
 */
@Tag("unit")
class MoneroRpcErrorTest {

    @Test
    fun rpcError_IsAMoneroError() {
        val error: MoneroError = MoneroRpcError("Method not found")
        assertEquals("Method not found", error.message)
        assertTrue(error is RuntimeException)
    }

    @Test
    fun rpcError_HasNoCodeUnlessItIsGiven() {
        assertNull(MoneroRpcError("Method not found").code)
        assertEquals(-32601, MoneroRpcError("Method not found", -32601).code)
        assertEquals(404, MoneroRpcError("Not found", 404).code)
    }

    @Test
    fun rpcError_KeepsItsCause() {
        val cause = IllegalStateException("cause")
        val error = MoneroRpcError("failed", 500, cause)
        assertSame(cause, error.cause)
        assertEquals(500, error.code)
    }

    @Test
    fun rpcError_IsCaughtAsAMoneroError() {
        assertFailsWith<MoneroError> { throw MoneroRpcError("Method not found") }
    }

    // an argument that monero-c refuses is not an answer of a server
    @Test
    fun errorOfMoneroC_ThatIsNotAnAnswerOfAServer_IsNotAnRpcError() {
        NativeTestSupport.assumeAvailable()
        val error = assertFailsWith<MoneroError> { MoneroUtils.validateAddress("not an address", monero.daemon.model.MoneroNetworkType.MAINNET) }
        assertFalse(error is MoneroRpcError, error.toString())
    }
}
