package monero.daemon.model

/**
 * Models the blocks that hold the results of a call that returns transactions: {"blocks":[...]}. A transaction is in its block, which has its height and its timestamp. The transactions that no block holds yet, in the pool, are in a block without a height.
 */
class MoneroBlocksResult {
    var blocks: List<MoneroBlock>? = null

    constructor()

    constructor(other: MoneroBlocksResult) {
        this.blocks = other.blocks?.map { it.copy() }
    }

    fun copy(): MoneroBlocksResult {
        return MoneroBlocksResult(this)
    }

    /**
     * Get the transactions of the blocks, in the order of the blocks.
     *
     * @return the transactions of the blocks
     */
    fun getTxs(): List<MoneroTx> {
        val txs = ArrayList<MoneroTx>()
        blocks?.forEach { block -> block.txs?.let { txs.addAll(it) } }
        return txs
    }
}
