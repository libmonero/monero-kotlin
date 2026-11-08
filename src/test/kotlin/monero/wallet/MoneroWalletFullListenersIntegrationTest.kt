package monero.wallet

import java.math.BigInteger
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroWalletListener
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir

/**
 * The notifications of a full wallet, against a regtest node, like the tests of monero-python (tests/test_monero_wallet_common.py and
 * test_monero_wallet_full.py) that sync a wallet with a listener, stop listening, and receive funds. They need monero-c and the regtest
 * node of src/test/docker.
 */
@Tag("integration")
class MoneroWalletFullListenersIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private val xmr = BigInteger.valueOf(1_000_000_000_000L)

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    // a wallet that is connected to the node and has not synced
    private fun newWallet(seed: String? = null, restoreHeight: Long? = null): MoneroWalletFull {
        val config = MoneroWalletConfig()
        config.path = tempDir.resolve("wallet-${System.nanoTime()}").toString()
        config.password = "supersecretpassword123"
        config.networkType = MoneroNetworkType.MAINNET
        config.server = RegtestSupport.daemonConnection()
        config.regtest = true
        config.seed = seed
        config.restoreHeight = restoreHeight
        return MoneroWalletFull.createWallet(config)
    }

    // a wallet with five coinbases that matured
    private fun fundedWallet(): MoneroWalletFull {
        val wallet = newWallet()
        try {
            RegtestSupport.mineBlocks(5, wallet.getPrimaryAddress())
            RegtestSupport.mineBlocks(130)
            wallet.sync()
            return wallet
        } catch (e: Throwable) {
            wallet.close()
            throw e
        }
    }

    private fun <T> eventually(timeoutMs: Long = 60_000, what: String, read: () -> T?): T {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            read()?.let { return it }
            if (System.nanoTime() > deadline) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(250)
        }
    }

    // Can sync with progress: the wallet of a seed that starts at the genesis block tells how far it is, to the listener of the call
    @Test
    fun testSyncWithProgress() {
        val seed = newWallet().use { it.getSeed() }
        newWallet(seed, 0).use { wallet ->
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
        val seed = newWallet().use { it.getSeed() }
        newWallet(seed, 0).use { wallet ->
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
        val seed = newWallet().use { it.getSeed() }
        newWallet(seed, 0).use { wallet ->
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

    // Can stop listening: a listener that is removed hears nothing more, and a wallet that is closed with one frees it
    @Test
    fun testStopListening() {
        newWallet().use { wallet ->
            val listener = WalletNotificationCollector()
            wallet.addListener(listener)
            assertEquals(listOf<MoneroWalletListener>(listener), wallet.getListeners())
            wallet.sync()
            RegtestSupport.mineBlocks(2)
            wallet.sync()
            eventually(what = "the new blocks") { listener.blockNotifications.size.takeIf { it >= 2 } }

            wallet.removeListener(listener)
            assertEquals(emptyList(), wallet.getListeners())
            val blocks = listener.blockNotifications.size
            listener.listening = false
            RegtestSupport.mineBlocks(2)
            wallet.sync()
            Thread.sleep(500)
            assertEquals(blocks, listener.blockNotifications.size)
            assertEquals(emptyList(), listener.errors)
        }
    }

    // Can be created and receive funds: the wallet that receives is notified of the output, the blocks and the balances, and the one that
    // sends of the output that it spent
    @Test
    fun testCreateAndReceive() {
        fundedWallet().use { sender ->
            newWallet().use { receiver ->
                val spent = WalletNotificationCollector()
                sender.addListener(spent)
                val received = WalletNotificationCollector()
                receiver.addListener(received)
                assertTrue(receiver.getListeners().isNotEmpty())

                val config = MoneroTxConfig()
                config.accountIndex = 0
                config.address = receiver.getPrimaryAddress()
                config.amount = xmr
                config.relay = true
                val sent = sender.createTx(config)
                val hash = assertNotNull(sent.hash)

                // the output is in the pool: the sync tells it before the block
                receiver.sync()
                val first = eventually(what = "the output in the pool") { received.outputsReceived.firstOrNull() }
                assertEquals(xmr, first.amount)
                assertEquals(0, first.accountIndex)
                assertEquals(0, first.subaddressIndex)
                assertEquals(hash, first.tx!!.hash)

                // the block that confirms it, and the ones that unlock it
                RegtestSupport.mineBlocks(12)
                receiver.sync()
                sender.sync()
                eventually(what = "the balances of the receiver") { received.balanceNotifications.lastOrNull()?.takeIf { it.second == xmr } }
                assertEquals(xmr, receiver.getBalance())
                assertEquals(xmr, receiver.getUnlockedBalance())
                assertTrue(received.blockNotifications.size >= 12)
                // the blocks that the wallet was notified of end at the last block of the chain
                eventually(what = "the last block") { received.blockNotifications.lastOrNull()?.takeIf { it == receiver.getDaemonHeight() - 1 } }

                // the sender spent an output of its own in that transaction
                eventually(what = "the output that the sender spent") { spent.outputsSpent.firstOrNull { it.tx!!.hash == hash } }

                // the output is notified each time that its transaction changes: in the pool, in a block, and unlocked
                val states = eventually(what = "the three notifications of the output") { received.getOutputsReceived(MoneroOutputQuery().also { it.accountIndex = 0 }).takeIf { it.size >= 3 } }
                assertEquals(3, states.size)
                assertTrue(states.all { it.tx!!.hash == hash && it.amount == xmr })
                assertEquals(listOf(false, true, true), states.map { it.tx!!.isConfirmed })
                assertEquals(listOf(true, true, false), states.map { it.tx!!.isLocked })
                assertEquals(listOf(null, states[1].tx!!.height, states[1].tx!!.height), states.map { it.tx!!.height })
                assertNotNull(states[1].tx!!.height)
                assertEquals(emptyList(), received.errors)
                assertEquals(emptyList(), spent.errors)
            }
        }
    }

    // A wallet that syncs in the background notifies a listener without being asked to sync
    @Test
    fun testReceiveWhileSyncingInTheBackground() {
        fundedWallet().use { sender ->
            newWallet().use { receiver ->
                val received = WalletNotificationCollector()
                receiver.addListener(received)
                receiver.startSyncing(1000)
                try {
                    val config = MoneroTxConfig()
                    config.accountIndex = 0
                    config.address = receiver.getPrimaryAddress()
                    config.amount = xmr
                    config.relay = true
                    val hash = assertNotNull(sender.createTx(config).hash)
                    RegtestSupport.mineBlocks(1)

                    val output = eventually(what = "the output") { received.outputsReceived.firstOrNull() }
                    assertEquals(hash, output.tx!!.hash)
                    eventually(what = "the new block") { received.blockNotifications.firstOrNull() }
                    assertEquals(emptyList(), received.errors)
                } finally {
                    receiver.stopSyncing()
                }
            }
        }
    }
}
