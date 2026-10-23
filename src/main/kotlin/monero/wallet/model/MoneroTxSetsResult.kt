package monero.wallet.model

/**
 * Models the result of the calls that make transactions: {"txSets":[...]}. The calls that sweep can make several sets, and the other calls make one.
 */
class MoneroTxSetsResult {
    var txSets: List<MoneroTxSet>? = null

    constructor()

    constructor(other: MoneroTxSetsResult) {
        this.txSets = other.txSets?.map { it.copy() }
    }

    fun copy(): MoneroTxSetsResult {
        return MoneroTxSetsResult(this)
    }

    /**
     * Get the transactions of the sets.
     *
     * @return the transactions of the sets
     */
    fun getTxs(): List<MoneroTxWallet> {
        val txs = ArrayList<MoneroTxWallet>()
        txSets?.forEach { txSet -> txSet.txs?.let { txs.addAll(it) } }
        return txs
    }
}
