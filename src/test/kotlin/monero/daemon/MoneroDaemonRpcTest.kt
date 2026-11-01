package monero.daemon

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.junit.jupiter.api.Tag
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.NativeTestSupport
import monero.common.MoneroError
import monero.daemon.model.MoneroBlockHeader
import monero.daemon.model.MoneroDaemonListener
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Daemon RPC integration tests. They need monero-c and a regtest node, which integration-tests.yml starts with the
 * docker-compose.yml of monero-c. Set the environment variable MONERO_C_TEST_DAEMON_URI to the URI of the node when it
 * is not the one of that file.
 */
@Tag("integration")
class MoneroDaemonRpcTest {

    private val daemonUri = System.getenv("MONERO_C_TEST_DAEMON_URI") ?: "http://127.0.0.1:18081"

    // the primary address of the test wallet of monero-python's config.ini, which the mining calls of these tests pay
    private val miningAddress = "48W9YHwPzRz9aPTeXCA6kmSpW6HsvmWx578jj3of2gT3JwZzwTf33amESBoNDkL6SVK34Q2HTKqgYbGyE1hBws3wCrcBDR2"

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

    @BeforeTest
    fun requireLibraryAndDaemon() {
        NativeTestSupport.assumeAvailable()
        assumeTrue(isDaemonUp(), "no monerod at $daemonUri: start the compose of src/test/integration, or set MONERO_C_TEST_DAEMON_URI")
    }

    @AfterTest
    fun closeClient() {
        http.close()
    }

    // Can get the blockchain height
    @Test
    fun testGetHeight() {
        MoneroDaemonRpc(daemonUri).use { daemon ->
            val height = daemon.getHeight()
            assertTrue(height > 0, "Height must be greater than 0")
        }
    }

    // The timeout wait returns null when no block arrives in the time
    @Test
    fun testWaitForNextBlockHeaderTimesOut() {
        MoneroDaemonRpc(daemonUri).use { daemon ->
            assertNull(daemon.waitForNextBlockHeader(150), "no block is mined while the daemon waits")
        }
    }

    // The timeout wait returns the header of the block that is mined while it waits
    @Test
    fun testWaitForNextBlockHeader() {
        MoneroDaemonRpc(daemonUri).use { daemon ->
            val header = CompletableFuture<MoneroBlockHeader?>()
            Thread {
                try {
                    header.complete(daemon.waitForNextBlockHeader(30000))
                } catch (e: Throwable) {
                    header.completeExceptionally(e)
                }
            }.apply { isDaemon = true; start() }

            // give the wait the time to register its listener, then mine for it
            Thread.sleep(500)
            mineBlocks(2)

            val arrived = header.get(35, TimeUnit.SECONDS)
            assertNotNull(arrived, "a block was mined while the daemon waited")
            assertTrue(arrived!!.height!! > 0, "the header of a mined block has a height")
        }
    }

    // The wait throws once the daemon is closed
    @Test
    fun testWaitForNextBlockHeaderOnClosedDaemon() {
        val daemon = MoneroDaemonRpc(daemonUri)
        daemon.close()
        val error = kotlin.runCatching { daemon.waitForNextBlockHeader(150) }.exceptionOrNull()
        assertTrue(error is MoneroError, "the wait on a closed daemon throws MoneroError, not ${error?.javaClass}")
    }

    // A listener receives the header of a block that is mined after it is added
    @Test
    fun testListenerReceivesBlockHeader() {
        MoneroDaemonRpc(daemonUri).use { daemon ->
            val arrived = CountDownLatch(1)
            var header: MoneroBlockHeader? = null
            daemon.addListener(object : MoneroDaemonListener() {
                override fun onBlockHeader(received: MoneroBlockHeader) {
                    header = received
                    arrived.countDown()
                }
            })

            mineBlocks(2)

            assertTrue(arrived.await(30, TimeUnit.SECONDS), "the listener received no block header")
            assertTrue(header!!.height!! > 0, "the header of a mined block has a height")
        }
    }

    // ------------------------------- PRIVATE ------------------------------------

    private fun isDaemonUp(): Boolean {
        return try {
            val request = HttpRequest.newBuilder(URI.create(daemonUri + "/get_height")).timeout(Duration.ofSeconds(2)).GET().build()
            http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200
        } catch (_: java.io.IOException) {
            false
        }
    }

    /**
     * Mine blocks on the regtest chain, paying the mining address.
     *
     * @param numBlocks is the number of blocks to mine
     * @throws MoneroError if the daemon does not mine them
     */
    private fun mineBlocks(numBlocks: Int) {
        val response = post("""{"jsonrpc": "2.0", "id": "0", "method": "generateblocks",""" +
            """ "params": {"amount_of_blocks": $numBlocks, "wallet_address": "$miningAddress", "starting_nonce": 0}}""", Duration.ofSeconds(120))
        if (!response.contains("\"status\": \"OK\"")) throw MoneroError("generateblocks did not mine: $response")
    }

    private fun post(body: String, timeout: Duration): String {
        val request = HttpRequest.newBuilder(URI.create(daemonUri + "/json_rpc"))
            .header("Content-Type", "application/json")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw MoneroError("The daemon answered ${response.statusCode()}: ${response.body()}")
        return response.body()
    }
}
