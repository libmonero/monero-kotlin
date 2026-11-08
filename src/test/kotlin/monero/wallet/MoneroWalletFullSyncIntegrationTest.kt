package monero.wallet

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroError
import monero.daemon.MoneroDaemonRpc
import org.junit.jupiter.api.Tag

/**
 * The sync of a full wallet, against a regtest node, like the tests of monero-python (tests/test_monero_wallet_full.py) that sync a wallet
 * with a random seed and that check that the wallets do not hear each other. They need monero-c and the regtest node of src/test/docker.
 */
@Tag("integration")
class MoneroWalletFullSyncIntegrationTest {

    @BeforeTest
    fun requireLibraryAndNode() {
        FullTestWallets.assumeAvailable()
    }

    // Can sync a wallet with a randomly generated seed: it syncs from its restore height, which monero-c leaves at 0 for a wallet that it
    // creates on the regtest chain, where monero-python expects the height of the chain, and it finds what a wallet of its seed that is
    // restored from there finds
    @Test
    fun testSyncRandom() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            FullTestWallets.create().use { wallet ->
                val restoreHeight = wallet.getRestoreHeight()
                val daemonHeight = daemon.getHeight()
                val startHeight = maxOf(restoreHeight, 1L)

                // the wallet before it syncs
                assertEquals(RegtestSupport.daemonUri, wallet.getDaemonConnection()!!.uri)
                assertEquals(daemonHeight, wallet.getDaemonHeight())
                assertTrue(restoreHeight <= daemonHeight)
                assertTrue(wallet.isConnectedToDaemon())
                assertFalse(wallet.isSynced())
                assertEquals(1L, wallet.getHeight())

                // sync it
                val progress = SyncProgressTester(wallet, startHeight, daemonHeight)
                val result = wallet.sync(progress)
                progress.onDone(wallet.getDaemonHeight())
                assertEquals(emptyList(), progress.errors)

                assertTrue(wallet.isConnectedToDaemon())
                assertTrue(wallet.isSynced())
                assertEquals(daemonHeight - startHeight, result.numBlocksFetched)
                assertEquals(false, result.receivedMoney)
                assertEquals(daemon.getHeight(), wallet.getHeight())

                // sync it with the defaults
                wallet.sync()
                assertTrue(wallet.isSynced())
                assertEquals(daemon.getHeight(), wallet.getHeight())

                // compare it with a ground truth
                FullTestWallets.create(wallet.getSeed(), restoreHeight).use { groundTruth ->
                    groundTruth.sync()
                    WalletEquality.assertFullOnChain(groundTruth, wallet)
                }
            }
        }
    }

    // Can not sync a wallet that is not connected to a node
    @Test
    fun testSyncUnconnectedWallet() {
        FullTestWallets.createUnconnected().use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.sync() }
            assertEquals("Wallet is not connected to daemon", error.message)
        }
    }

    // Does not interfere with other wallet notifications: two wallets of the same seed hear their own syncs, and a listener hears the wallet it
    // is registered with
    @Test
    fun testWalletsDoNotInterfere() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            val seed = FullTestWallets.create().use { it.getSeed() }
            val height = daemon.getHeight()
            val restoreHeight = height - 5
            FullTestWallets.create(seed, restoreHeight).use { wallet1 ->
                FullTestWallets.create(seed, restoreHeight).use { wallet2 ->
                    val tester1 = SyncProgressTester(wallet1, restoreHeight, height)
                    val tester2 = SyncProgressTester(wallet2, restoreHeight, height)
                    wallet1.addListener(tester1)
                    wallet2.addListener(tester2)

                    // sync the first wallet, and the second is not notified
                    wallet1.sync()
                    assertNotNull(tester1.prevHeight)
                    assertNull(tester2.prevHeight)

                    // sync the second, and a listener that is added to the first is not notified
                    val tester3 = SyncProgressTester(wallet1, restoreHeight, height)
                    wallet1.addListener(tester3)
                    wallet2.sync()
                    assertNotNull(tester2.prevHeight)
                    assertNull(tester3.prevHeight)
                    assertEquals(emptyList(), tester1.errors + tester2.errors + tester3.errors)
                }
            }
        }
    }
}
