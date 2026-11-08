package monero.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.wallet.model.MoneroWalletListener
import org.junit.jupiter.api.Tag

/**
 * The notifications of a full wallet, which [BaseMoneroWalletListenersTest] tests, and the ones that only a full wallet has, the
 * progress of a sync, like the tests of monero-python (test_monero_wallet_full.py) that sync a wallet with a listener.
 */
@Tag("integration")
class MoneroWalletFullListenersIntegrationTest : BaseMoneroWalletListenersTest() {

    override val wallets: TestWallets = FullTestWallets

    // Can sync with progress: the wallet of a seed that starts at the genesis block tells how far it is, to the listener of the call
    @Test
    fun testSyncWithProgress() {
        val seed = wallets.create().use { it.getSeed() }
        wallets.create(seed, 0).use { wallet ->
            val chainHeight = wallet.getDaemonHeight()
            assertEquals(1L, wallet.getHeight())
            assertFalse(wallet.isSynced())

            val tester = SyncProgressTester(wallet, wallet.getHeight(), chainHeight)
            val result = wallet.sync(tester)
            tester.onDone(wallet.getDaemonHeight())
            assertEquals(emptyList(), tester.errors)
            assertTrue(tester.prevHeight != null, "the sync notified its progress")

            assertTrue(wallet.isSynced())
            assertEquals(chainHeight, wallet.getHeight())
            assertTrue(result.numBlocksFetched!! > 0)
            assertEquals(false, result.receivedMoney)

            // the listener of the call is not registered, so it is not notified of the next sync
            assertEquals(emptyList(), wallet.getListeners())
            RegtestSupport.mineBlocks(2)
            wallet.sync()
            assertFalse(tester.notifiedAfterDone)
            assertEquals(emptyList(), tester.errors)
        }
    }

    // The sync returns after the listener of the call has been notified of all of it, even if the listener is slow
    @Test
    fun testSyncReturnsAfterTheListenerWasNotified() {
        val seed = wallets.create().use { it.getSeed() }
        wallets.create(seed, 0).use { wallet ->
            var notified = false
            val slow = object : MoneroWalletListener() {
                override fun onSyncProgress(height: Long, startHeight: Long, endHeight: Long, percentDone: Double, message: String?) {
                    Thread.sleep(500)
                    notified = true
                }
            }
            wallet.sync(slow)
            assertTrue(notified, "the listener was notified before the sync returned")
        }
    }

    // A listener that is registered is notified of every sync, and not after it is removed
    @Test
    fun testSyncWithARegisteredListener() {
        val seed = wallets.create().use { it.getSeed() }
        wallets.create(seed, 0).use { wallet ->
            val tester = SyncProgressTester(wallet, wallet.getHeight(), wallet.getDaemonHeight())
            wallet.addListener(tester)
            wallet.sync()
            tester.onDone(wallet.getDaemonHeight())
            assertEquals(emptyList(), tester.errors)

            // the next sync is notified too
            RegtestSupport.mineBlocks(3)
            wallet.sync()
            eventually(what = "the notification of the next sync") { tester.notifiedAfterDone.takeIf { it } }
            assertEquals(emptyList(), tester.errors)

            wallet.removeListener(tester)
            val seen = tester.prevHeight
            RegtestSupport.mineBlocks(2)
            wallet.sync()
            assertEquals(seen, tester.prevHeight)
        }
    }
}
