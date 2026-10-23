package monero.wallet.model

import java.math.BigInteger

/**
 * Receives notifications as a wallet is updated. Every method does nothing, so a listener overrides the ones it needs.
 */
open class MoneroWalletListener {

    /**
     * Invoked as the wallet is synchronized.
     *
     * @param height is the height of the synced block
     * @param startHeight is the starting height of the sync request
     * @param endHeight is the ending height of the sync request
     * @param percentDone is the sync progress as a percentage
     * @param message is a human-readable description of the current progress
     */
    open fun onSyncProgress(height: Long, startHeight: Long, endHeight: Long, percentDone: Double, message: String?) {
        // nothing to do
    }

    /**
     * Invoked when a new block is added to the chain.
     *
     * @param height is the height of the new block
     */
    open fun onNewBlock(height: Long) {
        // nothing to do
    }

    /**
     * Invoked when the wallet's balances change.
     *
     * @param newBalance is the new balance (atomic units)
     * @param newUnlockedBalance is the new unlocked balance (atomic units)
     */
    open fun onBalancesChanged(newBalance: BigInteger, newUnlockedBalance: BigInteger) {
        // nothing to do
    }

    /**
     * Invoked when the wallet receives an output.
     *
     * @param output is the output that the wallet received, in its transaction
     */
    open fun onOutputReceived(output: MoneroOutputWallet) {
        // nothing to do
    }

    /**
     * Invoked when the wallet spends an output.
     *
     * @param output is the output that the wallet spent, in its transaction
     */
    open fun onOutputSpent(output: MoneroOutputWallet) {
        // nothing to do
    }
}
