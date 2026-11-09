package monero.wallet

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroWalletListener
import org.junit.jupiter.api.Tag

/**
 * The scan of transactions and the rescans of a full wallet, which [BaseMoneroWalletScanTest] tests.
 */
@Tag("integration")
class MoneroWalletFullScanIntegrationTest : BaseMoneroWalletScanTest() {

    override val wallets: TestWallets = FullTestWallets

    // A listener whose first notification of an output is slow
    private class SlowListener : MoneroWalletListener() {
        val outputs = AtomicInteger()
        val slowNext = AtomicBoolean()

        override fun onOutputReceived(output: MoneroOutputWallet) {
            if (slowNext.compareAndSet(true, false)) Thread.sleep(1000)
            outputs.incrementAndGet()
        }
    }

    // The listeners that are registered have been notified of what a scan and a rescan found when they return, though the first notification is
    // slow: none comes after. The wallet waits for the thread of each listener. monero-c waits for its own queue too, but its callbacks only queue
    // what they get, so that wait is not seen here: this passes with a library that does not
    @Test
    fun testScanAndRescanNotifyBeforeTheyReturn() {
        wallets.create().use { wallet ->
            RegtestSupport.mineBlocks(5, wallet.getPrimaryAddress())
            RegtestSupport.mineBlocks(130)
            wallet.sync()
            val hash = checkNotNull(wallet.getTxs().first().hash)
            val listener = SlowListener()
            wallet.addListener(listener)

            listener.slowNext.set(true)
            wallet.scanTxs(listOf(hash))
            val afterScan = listener.outputs.get()
            assertTrue(afterScan > 0, "the scan notified the outputs that it found")
            Thread.sleep(1000)
            assertEquals(afterScan, listener.outputs.get(), "a notification of the scan came after it returned")

            listener.outputs.set(0)
            listener.slowNext.set(true)
            wallet.rescanBlockchain()
            val afterRescan = listener.outputs.get()
            assertTrue(afterRescan > 0, "the rescan notified the outputs that it found again")
            Thread.sleep(1000)
            assertEquals(afterRescan, listener.outputs.get(), "a notification of the rescan came after it returned")
        }
    }
}
