package monero.wallet

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroError
import monero.daemon.MoneroDaemonRpc

/**
 * The mining of a wallet, against a regtest node, like the test of monero-python (tests/test_monero_wallet_common.py) that starts and
 * stops mining, which every kind of wallet runs: a subclass says which kind. They need monero-c and the regtest node of src/test/docker.
 */
abstract class BaseMoneroWalletMiningTest {

    /**
     * The kind of wallet that the tests run for.
     */
    protected abstract val wallets: TestWallets

    @BeforeTest
    fun requireLibraryAndNode() {
        wallets.assumeAvailable()
    }

    // Runs the test with the node and a wallet connected to it, and leaves the node not mining, which stops it with the node and not with the
    // wallet: the wallet is what the tests test, and a stop of the wallet that failed in the cleanup of a test, with a node that was not
    // asked anything else, hid the result of the test. The error of a stop that finds the node stopped is ignored
    private fun withMining(test: (MoneroDaemonRpc, MoneroWalletNative) -> Unit) {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            wallets.create().use { wallet ->
                try {
                    if (daemon.getMiningStatus().isActive == true) daemon.stopMining()
                    test(daemon, wallet)
                } finally {
                    if (daemon.getMiningStatus().isActive == true) runCatching { daemon.stopMining() }
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

    // The defaults are one thread, not in the background and not on battery. The test leaves the stop to withMining
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
