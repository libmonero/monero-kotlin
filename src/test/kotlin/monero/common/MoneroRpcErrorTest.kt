package monero.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import monero.NativeTestSupport
import org.junit.jupiter.api.Tag

/**
 * The error of a daemon or a wallet RPC server that answers with an error, like MoneroRpcError of monero-python. What monero-c reports
 * for a real daemon is in the integration tests of the connection and the daemon, which have a server. The code is checked here with
 * a server of the JDK that answers as monerod does.
 */
@Tag("unit")
class MoneroRpcErrorTest {

    @Test
    fun testRpcErrorIsAMoneroError() {
        val error: MoneroError = MoneroRpcError("Method not found")
        assertEquals("Method not found", error.message)
        assertTrue(error is RuntimeException)
    }

    @Test
    fun testRpcErrorHasNoCodeUnlessItIsGiven() {
        assertNull(MoneroRpcError("Method not found").code)
        assertEquals(-32601, MoneroRpcError("Method not found", -32601).code)
        assertEquals(404, MoneroRpcError("Not found", 404).code)
    }

    @Test
    fun testRpcErrorKeepsItsCause() {
        val cause = IllegalStateException("cause")
        val error = MoneroRpcError("failed", 500, cause)
        assertSame(cause, error.cause)
        assertEquals(500, error.code)
    }

    @Test
    fun testRpcErrorIsCaughtAsAMoneroError() {
        assertFailsWith<MoneroError> { throw MoneroRpcError("Method not found") }
    }

    // the JSON-RPC code of the answer, and the HTTP status of an answer that is not OK, reach the error
    @Test
    fun testErrorOfAServerHasTheCodeThatTheServerAnsweredWith() {
        NativeTestSupport.assumeAvailable()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/json_rpc") { exchange ->
            exchange.requestBody.readBytes()
            val body = """{"id":"0","jsonrpc":"2.0","error":{"code":-32601,"message":"Method not found"}}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/") { exchange ->
            exchange.requestBody.readBytes()
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        try {
            MoneroRpcConnection("http://127.0.0.1:${server.address.port}").use { connection ->
                val json = assertFailsWith<MoneroRpcError> { connection.sendJsonRequest("invalid_method") }
                assertEquals("Method not found", json.message)
                assertEquals(-32601, json.code)

                val path = assertFailsWith<MoneroRpcError> { connection.sendPathRequest("invalid_method") }
                assertEquals(404, path.code, path.toString())
            }
        } finally {
            server.stop(0)
        }
    }

    // an argument that monero-c refuses is not an answer of a server
    @Test
    fun testErrorOfMoneroCThatIsNotAnAnswerOfAServerIsNotAnRpcError() {
        NativeTestSupport.assumeAvailable()
        val error = assertFailsWith<MoneroError> { MoneroUtils.validateAddress("not an address", monero.daemon.model.MoneroNetworkType.MAINNET) }
        assertFalse(error is MoneroRpcError, error.toString())
    }
}
