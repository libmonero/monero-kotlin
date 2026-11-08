package monero.wallet

import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroWalletListener

/**
 * The notifications of a wallet, against a regtest node, like the tests of monero-python (tests/test_monero_wallet_common.py) that stop
 * listening and receive funds, which every kind of wallet runs: a subclass says which kind. The progress of a sync is not here, since the
 * wallet of a server does not report it. They need monero-c and the regtest node of src/test/docker.
 */
abstract class BaseMoneroWalletListenersTest {

    /**
     * The kind of wallet that the tests run for.
     */
    protected abstract val wallets: TestWallets

    protected val xmr: BigInteger = BigInteger.valueOf(1_000_000_000_000L)

    @BeforeTest
    fun requireLibraryAndNode() {
        wallets.assumeAvailable()
    }

    // a wallet with five coinbases that matured
    protected fun fundedWallet(): MoneroWalletNative {
        val wallet = wallets.create()
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

    protected fun <T> eventually(timeoutMs: Long = 60_000, what: String, read: () -> T?): T {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            read()?.let { return it }
            if (System.nanoTime() > deadline) throw AssertionError("Timed out waiting for $what")
            Thread.sleep(250)
        }
    }

    // Can stop listening: a listener that is removed hears nothing more, and a wallet that is closed with one frees it
    @Test
    fun testStopListening() {
        wallets.create().use { wallet ->
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

    /**
     * The states of the transaction of an output, as whether the transaction is confirmed and whether it is locked, that the receiver is
     * notified of, in order. A full wallet is notified when the transaction is in the pool, when it is in a block, and when it unlocks.
     */
    protected open val outputStates: List<Pair<Boolean, Boolean>> = listOf(false to true, true to true, true to false)

    // Can be created and receive funds: the wallet that receives is notified of the output, the blocks and the balances, and the one that
    // sends of the output that it spent
    @Test
    fun testCreateAndReceive() {
        fundedWallet().use { sender ->
            wallets.create().use { receiver ->
                val spent = WalletNotificationCollector()
                sender.addListener(spent)
                val received = WalletNotificationCollector()
                receiver.addListener(received)
                assertTrue(receiver.getListeners().isNotEmpty())

                // a wallet of a server compares each of its polls with the one before, and notifies from its second poll on
                sender.sync()
                receiver.sync()

                val config = MoneroTxConfig()
                config.accountIndex = 0
                config.address = receiver.getPrimaryAddress()
                config.amount = xmr
                config.relay = true
                val sent = sender.createTx(config)
                val hash = assertNotNull(sent.hash)

                // the transaction is in the pool, and then in a block
                receiver.sync()
                sender.sync()
                RegtestSupport.mineBlocks(1)
                receiver.sync()
                sender.sync()
                val first = eventually(what = "the first output") { received.outputsReceived.firstOrNull() }
                assertEquals(xmr, first.amount)
                assertEquals(0, first.accountIndex)
                assertEquals(0, first.subaddressIndex)
                assertEquals(hash, first.tx!!.hash)

                // the sender spent an output of its own in that transaction
                eventually(what = "the output that the sender spent") { spent.outputsSpent.firstOrNull { it.tx!!.hash == hash } }

                // the blocks that unlock it
                RegtestSupport.mineBlocks(11)
                receiver.sync()
                sender.sync()
                eventually(what = "the balances of the receiver") { received.balanceNotifications.lastOrNull()?.takeIf { it.second == xmr } }
                assertEquals(xmr, receiver.getBalance())
                assertEquals(xmr, receiver.getUnlockedBalance())
                assertTrue(received.blockNotifications.size >= 12)
                // the blocks that the wallet was notified of end at the last block of the chain
                eventually(what = "the last block") { received.blockNotifications.lastOrNull()?.takeIf { it == receiver.getHeight() - 1 } }

                // the output is notified each time that its transaction changes
                val states = eventually(what = "the notifications of the output") { received.getOutputsReceived(MoneroOutputQuery().also { it.accountIndex = 0 }).takeIf { it.size >= outputStates.size } }
                assertEquals(outputStates.size, states.size)
                assertTrue(states.all { it.tx!!.hash == hash && it.amount == xmr })
                assertEquals(outputStates.map { it.first }, states.map { it.tx!!.isConfirmed })
                assertEquals(outputStates.map { it.second }, states.map { it.tx!!.isLocked })
                val height = assertNotNull(states.last().tx!!.height)
                assertEquals(states.map { if (it.tx!!.isConfirmed == true) height else null }, states.map { it.tx!!.height })
                assertEquals(emptyList(), received.errors)
                assertEquals(emptyList(), spent.errors)
            }
        }
    }

    // A wallet that syncs in the background notifies a listener without being asked to sync
    @Test
    fun testReceiveWhileSyncingInTheBackground() {
        fundedWallet().use { sender ->
            wallets.create().use { receiver ->
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
