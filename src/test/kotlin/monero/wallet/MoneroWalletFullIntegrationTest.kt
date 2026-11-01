package monero.wallet

import java.math.BigInteger
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroError
import monero.common.MoneroRpcConnection
import monero.daemon.MoneroDaemonRpc
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir

/**
 * Full wallet integration tests. They need monero-c and a regtest node, which integration-tests.yml starts with the
 * docker-compose.yml of monero-c, and they move its chain: they mine to the wallets that they create, and pay with
 * what they mine. What they assert about the chain is relative to the chain that they find.
 */
@Tag("integration")
class MoneroWalletFullIntegrationTest {

    // the coinbase of a block is locked for 60 blocks, and a block that spends it needs one more
    private val blocksToUnlock = 61

    @TempDir
    lateinit var tempDir: Path

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    // A random wallet scans a regtest chain from the genesis block, and reaches the height of the daemon
    @Test
    fun testSyncFromGenesis() {
        MoneroDaemonRpc(RegtestSupport.daemonUri).use { daemon ->
            createWallet().use { wallet ->
                assertTrue(wallet.isConnectedToDaemon())
                assertFalse(wallet.isSynced())
                assertEquals(1L, wallet.getHeight())

                wallet.sync()

                val daemonHeight = daemon.getHeight()
                assertTrue(wallet.isSynced())
                assertEquals(daemonHeight, wallet.getDaemonHeight())
                assertEquals(daemonHeight, wallet.getHeight())
                assertEquals(BigInteger.ZERO, wallet.getBalance())
            }
        }
    }

    // The blocks that a wallet gets mined are its balance once it syncs, and unlocked once they have matured
    @Test
    fun testBalanceOfMinedBlocks() {
        createWallet().use { wallet ->
            RegtestSupport.mineBlocks(2, wallet.getPrimaryAddress())
            wallet.sync()
            assertTrue(wallet.getBalance().signum() > 0, "the wallet got the blocks that were mined for it")
            assertEquals(BigInteger.ZERO, wallet.getUnlockedBalance())

            RegtestSupport.mineBlocks(blocksToUnlock)
            wallet.sync()
            assertTrue(wallet.getUnlockedBalance().signum() > 0)
            assertEquals(wallet.getBalance(), wallet.getUnlockedBalance())
        }
    }

    // A wallet that syncs in the background gets the blocks that are mined after it started
    @Test
    fun testSyncInTheBackground() {
        createWallet().use { wallet ->
            wallet.startSyncing(500)
            try {
                RegtestSupport.mineBlocks(1, wallet.getPrimaryAddress())
                val deadline = System.nanoTime() + 60_000_000_000L
                while (wallet.getBalance().signum() == 0) {
                    if (System.nanoTime() > deadline) throw MoneroError("the wallet did not sync the block that was mined for it")
                    Thread.sleep(500)
                }
            } finally {
                wallet.stopSyncing()
            }
        }
    }

    // The wait for the next block returns the height of the block that is mined while the wallet syncs
    @Test
    fun testWaitForNextBlock() {
        createWallet().use { wallet ->
            wallet.sync()
            wallet.startSyncing(500)
            try {
                assertNull(wallet.waitForNextBlock(300), "no block is mined while the wallet waits")

                // the wait holds the thread of the wallet, so the block is mined from another one
                val mined = CompletableFuture.runAsync {
                    Thread.sleep(500)
                    RegtestSupport.mineBlocks(1)
                }
                val height = wallet.waitForNextBlock(30_000)
                mined.get(60, TimeUnit.SECONDS)

                assertNotNull(height, "a block was mined while the wallet waited")
                assertTrue(height > 0)
            } finally {
                wallet.stopSyncing()
            }
        }
    }

    // A sync that runs is asked to stop from another thread, and the wallet closes after it
    @Test
    fun testRequestShutdownStopsASync() {
        val config = config()
        val wallet = MoneroWalletFull.createWallet(config)
        try {
            RegtestSupport.mineBlocks(300, wallet.getPrimaryAddress())
            val sync = CompletableFuture.supplyAsync { wallet.sync() }
            Thread.sleep(100)

            wallet.requestShutdown()

            // the sync ends, whether it was stopped or it had finished, and the calls that need the daemon fail from then on
            runCatching { sync.get(60, TimeUnit.SECONDS) }
            assertFailsWith<MoneroError> { wallet.sync() }
        } finally {
            wallet.close()
        }
        assertTrue(wallet.isClosed())
    }

    // A wallet that is reopened keeps what it scanned, so it does not scan the chain again
    @Test
    fun testReopenKeepsTheChain() {
        val config = config()
        val created = MoneroWalletFull.createWallet(config)
        val height: Long
        val balance: BigInteger
        val seed: String
        try {
            RegtestSupport.mineBlocks(2, created.getPrimaryAddress())
            created.sync()
            height = created.getHeight()
            balance = created.getBalance()
            seed = created.getSeed()
            assertTrue(balance.signum() > 0)
        } finally {
            created.close(true)
        }

        MoneroWalletFull.openWallet(config).use { wallet ->
            assertEquals(seed, wallet.getSeed())
            assertEquals(height, wallet.getHeight())
            assertEquals(balance, wallet.getBalance())
        }
    }

    // ------------------------------- PRIVATE ------------------------------------

    private fun config(): MoneroWalletConfig {
        val config = MoneroWalletConfig()
        config.path = tempDir.resolve("wallet-${System.nanoTime()}").toString()
        config.password = "supersecretpassword123"
        config.networkType = MoneroNetworkType.MAINNET
        config.server = MoneroRpcConnection(RegtestSupport.daemonUri)
        config.regtest = true
        return config
    }

    private fun createWallet(): MoneroWalletFull {
        return MoneroWalletFull.createWallet(config())
    }
}
