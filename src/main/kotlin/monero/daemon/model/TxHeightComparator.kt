package monero.daemon.model

/**
 * Compares two transactions by the height of their blocks, the lowest first. A transaction that is not in a block, so one that is
 * not confirmed, goes after the ones that are, and the order of two in the same block is the one that they have in it.
 */
class TxHeightComparator : Comparator<MoneroTx> {

    override fun compare(tx1: MoneroTx, tx2: MoneroTx): Int {
        val height1 = tx1.height
        val height2 = tx2.height
        if (height1 == null && height2 == null) return 0
        if (height1 == null) return 1
        if (height2 == null) return -1
        val diff = height1.compareTo(height2)
        if (diff != 0) return diff

        // in the same block, so they keep the order that they have there
        val txs = tx1.block?.txs ?: return 0
        return txs.indexOf(tx1).compareTo(txs.indexOf(tx2))
    }
}
