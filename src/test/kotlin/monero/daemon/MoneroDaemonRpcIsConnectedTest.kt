package monero.daemon

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import monero.NativeTestSupport
import org.junit.jupiter.api.Tag

/**
 * What [MoneroDaemonRpc.isConnected] answers: it asks the daemon each time, so it follows a daemon that stops, and a daemon that
 * does not answer is false and not an error. The daemon is a server of the JDK that answers the version as monerod does.
 */
@Tag("unit")
class MoneroDaemonRpcIsConnectedTest {

    private fun versionServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/json_rpc") { exchange ->
            exchange.requestBody.readBytes()
            val body = """{"id":"0","jsonrpc":"2.0","result":{"release":true,"status":"OK","untrusted":false,"version":196621}}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        return server
    }

    @Test
    fun testIsConnectedFollowsTheDaemon() {
        NativeTestSupport.assumeAvailable()
        val server = versionServer()
        try {
            MoneroDaemonRpc("http://127.0.0.1:${server.address.port}").use { daemon ->
                assertTrue(daemon.isConnected())

                // asked again, it finds out that the daemon is gone
                server.stop(0)
                assertFalse(daemon.isConnected())
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testIsConnectedOfADaemonThatDoesNotAnswerIsFalseAndNotAnError() {
        NativeTestSupport.assumeAvailable()

        // the port of a server that is stopped
        val server = versionServer()
        val port = server.address.port
        server.stop(0)
        MoneroDaemonRpc("http://127.0.0.1:$port").use { daemon ->
            assertFalse(daemon.isConnected())
        }
    }
}
