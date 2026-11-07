package monero.wallet.model

import monero.daemon.model.compareTxsByHeight

/**
 * Compares two incoming transfers by the height of their transactions, then by their account and then by their subaddress, the
 * lowest first. A transfer that is not in a transaction goes after the ones that are, and one without an index goes before the ones
 * that have it.
 */
class IncomingTransferComparator : Comparator<MoneroIncomingTransfer> {

    override fun compare(t1: MoneroIncomingTransfer, t2: MoneroIncomingTransfer): Int {
        val byHeight = compareTxsByHeight(t1.tx, t2.tx)
        if (byHeight != 0) return byHeight
        val byAccount = compareValues(t1.accountIndex, t2.accountIndex)
        if (byAccount != 0) return byAccount
        return compareValues(t1.subaddressIndex, t2.subaddressIndex)
    }
}
