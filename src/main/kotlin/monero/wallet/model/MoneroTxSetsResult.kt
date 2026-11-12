package monero.wallet.model

/**
 * Models the normalized result of calls that make transactions. ABI 0.3 returns sets under `txSets`; ABI 0.4 returns a single set directly,
 * except `sweepUnlocked`, which can make several sets. The native binding normalizes both shapes here.
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
