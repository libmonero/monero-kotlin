package monero.wallet

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroTxQuery

/**
 * Tests what a wallet notifies as it syncs, like WalletSyncTester of monero-python: besides the progress, the blocks come one after the other
 * from the height that was asked for, the balances only change, every output that the wallet received or spent is a whole output of a
 * transaction, and the balance is what the outputs say. The listener is registered with the wallet, so it is also notified of the syncs
 * that come after the first one. The transaction of an output that monero-c notifies has its hash, its unlock time and the output, and, when
 * the output is found in a block, its version, its extra and its miner flag, as monero-python checks: the transaction of an output that unlocks
 * comes from the transactions of the wallet and has none of them (the TODO.md of monero-c has it).
 *
 * @param wallet is the wallet to test
 * @param startHeight is the height that the sync starts at
 * @param endHeight is the height of the chain that the sync ends at
 */
class WalletSyncTester(wallet: MoneroWalletNative, startHeight: Long, endHeight: Long) : SyncProgressTester(wallet, startHeight, endHeight) {

    // the height that was asked for, which the progress notifications do not move
    private val syncStartHeight = startHeight

    @Volatile
    private var prevBlockHeight: Long? = null

    @Volatile
    private var prevOutputReceived: MoneroOutputWallet? = null

    @Volatile
    private var prevOutputSpent: MoneroOutputWallet? = null

    @Volatile
    private var prevBalance: BigInteger? = null

    @Volatile
    private var prevUnlockedBalance: BigInteger? = null

    @Volatile
    private var incomingTotal = BigInteger.ZERO

    @Volatile
    private var outgoingTotal = BigInteger.ZERO

    /** whether a new block was notified after [onDone] */
    @Volatile
    var newBlockAfterDone = false
        private set

    init {
        require(startHeight >= 0) { "Invalid start height provided: $startHeight" }
        require(endHeight >= 0) { "Invalid end height provided: $endHeight" }
    }

    override fun onNewBlock(height: Long) = record {
        if (isDone) {
            check(this in wallet.getListeners()) { "Listener has completed and is not registered so should not be called again" }
            newBlockAfterDone = true
        }
        prevBlockHeight?.let { check(it + 1 == height) { "Expected height ${it + 1}, got $height" } }
        // the blocks start at the height that was asked for, not at the base of the progress
        check(height >= syncStartHeight) { "The block $height is before the start of the sync, $syncStartHeight" }
        prevBlockHeight = height
    }

    override fun onBalancesChanged(newBalance: BigInteger, newUnlockedBalance: BigInteger) = record {
        prevBalance?.let {
            check(newBalance != it || newUnlockedBalance != prevUnlockedBalance) {
                "The balances are notified and did not change: $newBalance, $newUnlockedBalance"
            }
        }
        prevBalance = newBalance
        prevUnlockedBalance = newUnlockedBalance
    }

    private fun testOutput(output: MoneroOutputWallet, received: Boolean) = record {
        if (received) prevOutputReceived = output else prevOutputSpent = output

        val amount = checkNotNull(output.amount) { "the output has no amount" }
        check((output.accountIndex ?: -1) >= 0) { "the output has no account" }
        // a spent output can have no subaddress, since the server gives no inputs and one is made from the outgoing transfer
        if (received) check((output.subaddressIndex ?: -1) >= 0) { "the received output has no subaddress" }

        val tx = checkNotNull(output.tx as? monero.wallet.model.MoneroTxWallet) { "the output has no transaction of a wallet" }
        check(tx.hash?.length == 64) { "the hash of the transaction is not a hash: ${tx.hash}" }
        check(tx.unlockTime != null && tx.unlockTime!!.signum() >= 0) { "the transaction has no unlock time" }

        // the outputs are notified when their block is found, so they are locked then, and their transaction is the one of the block
        if (tx.isLocked == true) {
            check((tx.version ?: -1) >= 0) { "the transaction of ${tx.hash} has no version" }
            check(tx.extra?.isNotEmpty() == true) { "the transaction of ${tx.hash} has no extra" }
            check(tx.isMinerTx != null) { "the transaction of ${tx.hash} does not say if it is a miner transaction" }
            if (received) incomingTotal += amount else outgoingTotal += amount
        }
    }

    override fun onOutputReceived(output: MoneroOutputWallet) {
        testOutput(output, true)
    }

    override fun onOutputSpent(output: MoneroOutputWallet) {
        testOutput(output, false)
    }

    /**
     * Test that the notifications of the sync add up to the wallet: it was notified of blocks, of outputs received and spent, and its balances
     * are the ones of the last notification and the difference of what the outputs received and spent, less what the transactions in the pool
     * move, which the notifications of the outputs do not have.
     */
    override fun onDone(chainHeight: Long) {
        super.onDone(chainHeight)
        assertEquals(emptyList(), errors)

        assertNotNull(prevBlockHeight)
        assertNotNull(prevOutputReceived)
        assertNotNull(prevOutputSpent)
        var expectedBalance = incomingTotal - outgoingTotal

        // the outputs that the pool spends are not in the notifications
        var poolSpend = BigInteger.ZERO
        for (poolTx in wallet.getTxs(MoneroTxQuery().also { it.inTxPool = true })) {
            poolSpend += poolTx.getOutgoingAmount() + poolTx.fee!!
        }
        expectedBalance -= poolSpend

        val balance = wallet.getBalance()
        val unlockedBalance = wallet.getUnlockedBalance()
        assertEquals(expectedBalance, balance, "the balance that the outputs say")
        assertEquals(prevBalance, balance, "the balance of the last notification")
        assertEquals(prevUnlockedBalance, unlockedBalance, "the unlocked balance of the last notification")
        assertTrue(balance.signum() >= 0)
    }
}
