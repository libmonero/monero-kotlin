package monero.wallet

import java.math.BigInteger
import java.util.Collections
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroWalletListener

/**
 * Collects the blocks, the balances and the outputs that a wallet notifies, like WalletNotificationCollector of monero-python. A
 * notification runs on a thread of the wallet, where an assertion that fails would be lost, so the checks of the collector are kept in
 * [errors] for the test to look at.
 */
class WalletNotificationCollector : MoneroWalletListener() {

    /** whether the listener is expected to be notified: a notification when it is false is an error */
    @Volatile
    var listening = true

    val blockNotifications: MutableList<Long> = Collections.synchronizedList(ArrayList())
    val balanceNotifications: MutableList<Pair<BigInteger, BigInteger>> = Collections.synchronizedList(ArrayList())
    val outputsReceived: MutableList<MoneroOutputWallet> = Collections.synchronizedList(ArrayList())
    val outputsSpent: MutableList<MoneroOutputWallet> = Collections.synchronizedList(ArrayList())
    val errors: MutableList<Throwable> = Collections.synchronizedList(ArrayList())

    private fun collect(notification: () -> Unit) {
        try {
            check(listening) { "The listener is notified when it is not listening" }
            notification()
        } catch (e: Throwable) {
            errors.add(e)
        }
    }

    override fun onNewBlock(height: Long) = collect {
        synchronized(blockNotifications) {
            // the blocks come one after the other
            blockNotifications.lastOrNull()?.let { check(height == it + 1) { "Expected height ${it + 1}, got $height" } }
            blockNotifications.add(height)
        }
    }

    override fun onBalancesChanged(newBalance: BigInteger, newUnlockedBalance: BigInteger) = collect {
        synchronized(balanceNotifications) {
            // the balances change
            balanceNotifications.lastOrNull()?.let { check(newBalance != it.first || newUnlockedBalance != it.second) { "The balances did not change: $newBalance, $newUnlockedBalance" } }
            balanceNotifications.add(newBalance to newUnlockedBalance)
        }
    }

    override fun onOutputReceived(output: MoneroOutputWallet) = collect {
        outputsReceived.add(output)
    }

    override fun onOutputSpent(output: MoneroOutputWallet) = collect {
        outputsSpent.add(output)
    }

    fun getOutputsReceived(query: MoneroOutputQuery): List<MoneroOutputWallet> = synchronized(outputsReceived) { outputsReceived.filter { query.meetsCriteria(it) } }

    fun getOutputsSpent(query: MoneroOutputQuery): List<MoneroOutputWallet> = synchronized(outputsSpent) { outputsSpent.filter { query.meetsCriteria(it) } }
}
