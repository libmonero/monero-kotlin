package monero.daemon

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.NativeTestSupport
import monero.common.MoneroError
import monero.daemon.model.MoneroBlockHeader
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag

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
        assumeTrue(isDaemonUp(), "no monerod at $daemonUri: start the compose of monero-c, or set MONERO_C_TEST_DAEMON_URI")
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

            // give the wait the time to start, then mine for it
            Thread.sleep(500)
            mineBlocks(2)

            val arrived = header.get(35, TimeUnit.SECONDS)
            assertNotNull(arrived, "a block was mined while the daemon waited")
            assertTrue(arrived.height!! > 0, "the header of a mined block has a height")
        }
    }

    // The other calls of the daemon run while a timeout wait does
    @Test
    fun testCallsRunWhileTheDaemonWaits() {
        MoneroDaemonRpc(daemonUri).use { daemon ->
            val wait = CompletableFuture.supplyAsync { daemon.waitForNextBlockHeader(2000) }
            Thread.sleep(300)

            val started = System.nanoTime()
            assertTrue(daemon.getHeight() > 0)
            assertTrue(System.nanoTime() - started < TimeUnit.MILLISECONDS.toNanos(1500), "getHeight waited for the wait to time out")

            assertNull(wait.get(10, TimeUnit.SECONDS))
        }
    }

    // Close waits for a timeout wait that is running, and does not free the handle under it
    @Test
    fun testCloseWaitsForTheWaitThatRuns() {
        val daemon = MoneroDaemonRpc(daemonUri)
        val wait = CompletableFuture.supplyAsync { daemon.waitForNextBlockHeader(1000) }
        Thread.sleep(300)

        daemon.close()

        assertNull(wait.get(10, TimeUnit.SECONDS), "the wait ended with its timeout, not with a crash")
    }

    // The wait throws once the daemon is closed
    @Test
    fun testWaitForNextBlockHeaderOnClosedDaemon() {
        val daemon = MoneroDaemonRpc(daemonUri)
        daemon.close()
        assertFailsWith<MoneroError> { daemon.waitForNextBlockHeader(150) }
    }

    // A timeout out of range is an error of the C ABI
    @Test
    fun testWaitForNextBlockHeaderWithoutATimeout() {
        MoneroDaemonRpc(daemonUri).use { daemon ->
            assertFailsWith<MoneroError> { daemon.waitForNextBlockHeader(0) }
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
        val body = """{"jsonrpc": "2.0", "id": "0", "method": "generateblocks",""" +
            """ "params": {"amount_of_blocks": $numBlocks, "wallet_address": "$miningAddress", "starting_nonce": 0}}"""
        val request = HttpRequest.newBuilder(URI.create(daemonUri + "/json_rpc"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200 || !response.body().contains("\"status\": \"OK\"")) {
            throw MoneroError("generateblocks did not mine: ${response.body()}")
        }
    }
}
