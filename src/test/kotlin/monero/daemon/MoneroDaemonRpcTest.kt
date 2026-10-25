package monero.daemon

import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Daemon RPC integration tests. They need monero-c and a regtest node, which integration-tests.yml starts with the
 * docker-compose.yml of monero-c. Set the environment variable MONERO_C_TEST_DAEMON_URI to the URI of the node when it
 * is not the one of that file.
 */
@Tag("integration")
class MoneroDaemonRpcTest {

    private val daemonUri = System.getenv("MONERO_C_TEST_DAEMON_URI") ?: "http://127.0.0.1:18081"

    // Can get the blockchain height
    @Test
    fun testGetHeight() {
        MoneroDaemonRpc(daemonUri).use { daemon ->
            val height = daemon.getHeight()
            assertTrue(height > 0, "Height must be greater than 0")
        }
    }
}
