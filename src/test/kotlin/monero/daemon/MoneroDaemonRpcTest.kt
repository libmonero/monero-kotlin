package monero.daemon

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroError
import monero.common.MoneroRpcError
import monero.daemon.model.MoneroBlockHeader
import org.junit.jupiter.api.Tag

/**
 * Daemon RPC integration tests. They need monero-c and a regtest node, which integration-tests.yml starts with the
 * docker-compose.yml of src/test/docker. RegtestSupport says how to point them at another node.
 */
@Tag("integration")
class MoneroDaemonRpcTest {

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    // Can get the blockchain height
    @Test
    fun testGetHeight() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            val height = daemon.getHeight()
            assertTrue(height > 0, "Height must be greater than 0")
        }
    }

    // A block that the daemon does not have is an answer of the daemon with an error
    @Test
    fun testGetBlockByUnknownHash() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            assertFailsWith<MoneroRpcError> { daemon.getBlockByHash("0".repeat(64)) }
        }
    }

    // The timeout wait returns null when no block arrives in the time
    @Test
    fun testWaitForNextBlockHeaderTimesOut() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            assertNull(daemon.waitForNextBlockHeader(150), "no block is mined while the daemon waits")
        }
    }

    // The timeout wait returns the header of the block that is mined while it waits
    @Test
    fun testWaitForNextBlockHeader() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
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
            RegtestSupport.mineBlocks(2)

            val arrived = header.get(35, TimeUnit.SECONDS)
            assertNotNull(arrived, "a block was mined while the daemon waited")
            assertTrue(arrived.height!! > 0, "the header of a mined block has a height")
        }
    }

    // The other calls of the daemon run while a timeout wait does
    @Test
    fun testCallsRunWhileTheDaemonWaits() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
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
        val daemon = MoneroDaemonRpc(RegtestSupport.daemonConnection())
        val wait = CompletableFuture.supplyAsync { daemon.waitForNextBlockHeader(1000) }
        Thread.sleep(300)

        daemon.close()

        assertNull(wait.get(10, TimeUnit.SECONDS), "the wait ended with its timeout, not with a crash")
    }

    // The wait throws once the daemon is closed
    @Test
    fun testWaitForNextBlockHeaderOnClosedDaemon() {
        val daemon = MoneroDaemonRpc(RegtestSupport.daemonConnection())
        daemon.close()
        assertFailsWith<MoneroError> { daemon.waitForNextBlockHeader(150) }
    }

    // A timeout out of range is an error of the C ABI
    @Test
    fun testWaitForNextBlockHeaderWithoutATimeout() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            assertFailsWith<MoneroError> { daemon.waitForNextBlockHeader(0) }
        }
    }
}
