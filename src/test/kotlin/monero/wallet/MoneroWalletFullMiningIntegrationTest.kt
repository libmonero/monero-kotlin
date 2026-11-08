package monero.wallet

import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroError
import monero.daemon.MoneroDaemonRpc
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir

/**
 * The mining of a full wallet, against a regtest node, like the test of monero-python (tests/test_monero_wallet_common.py) that starts and
 * stops mining. They need monero-c and the regtest node of src/test/docker.
 */
@Tag("integration")
class MoneroWalletFullMiningIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    private fun newWallet(): MoneroWalletFull {
        val config = MoneroWalletConfig()
        config.path = tempDir.resolve("wallet-${System.nanoTime()}").toString()
        config.password = "supersecretpassword123"
        config.networkType = MoneroNetworkType.MAINNET
        config.server = RegtestSupport.daemonConnection()
        config.regtest = true
        return MoneroWalletFull.createWallet(config)
    }

    // runs the test with the node and a wallet connected to it, and leaves the node not mining
    private fun withMining(test: (MoneroDaemonRpc, MoneroWalletFull) -> Unit) {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            newWallet().use { wallet ->
                try {
                    if (daemon.getMiningStatus().isActive == true) wallet.stopMining()
                    test(daemon, wallet)
                } finally {
                    if (daemon.getMiningStatus().isActive == true) wallet.stopMining()
                }
            }
        }
    }

    // Can start and stop mining: the node mines to the address of the wallet with the threads that were asked for
    @Test
    fun testMining() {
        withMining { daemon, wallet ->
            wallet.startMining(1, false, true)
            val status = daemon.getMiningStatus()
            assertEquals(true, status.isActive)
            assertEquals(false, status.isBackground)
            assertEquals(1, status.numThreads)
            assertEquals(wallet.getPrimaryAddress(), status.address)

            wallet.stopMining()
            assertEquals(false, daemon.getMiningStatus().isActive)
        }
    }

    @Test
    fun testMiningInTheBackgroundWithThreads() {
        withMining { daemon, wallet ->
            wallet.startMining(2, true, null)
            val status = daemon.getMiningStatus()
            assertEquals(true, status.isActive)
            assertEquals(true, status.isBackground)
            assertEquals(2, status.numThreads)
            wallet.stopMining()
        }
    }

    // The defaults are one thread, not in the background and not on battery. The node was found not mining when a test stopped what the defaults
    // had started, so this test does not stop it: withMining stops it if it is still on
    @Test
    fun testMiningWithTheDefaults() {
        withMining { daemon, wallet ->
            wallet.startMining()
            val status = daemon.getMiningStatus()
            assertEquals(true, status.isActive)
            assertEquals(false, status.isBackground)
            assertEquals(1, status.numThreads)
        }
    }

    // Can not start mining when the node mines, or stop it when it does not
    @Test
    fun testMiningThatIsNotPossible() {
        withMining { daemon, wallet ->
            assertFailsWith<MoneroError> { wallet.stopMining() }
            wallet.startMining(1, false, true)
            assertFailsWith<MoneroError> { wallet.startMining(1, false, true) }
            assertTrue(daemon.getMiningStatus().isActive == true, "the node keeps mining")
            wallet.stopMining()
            assertFalse(daemon.getMiningStatus().isActive == true)
            assertFailsWith<MoneroError> { wallet.stopMining() }
        }
    }
}
