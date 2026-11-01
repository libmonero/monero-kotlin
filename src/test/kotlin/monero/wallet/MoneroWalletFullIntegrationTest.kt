package monero.wallet

import java.math.BigInteger
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
