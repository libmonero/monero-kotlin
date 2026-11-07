package monero.wallet.model

import monero.daemon.model.compareTxsByHeight

/**
 * Compares two outputs of a wallet by the height of their transactions, then by their account, their subaddress, their index and
 * the hexadecimal of their key image, the lowest first. An output that is not in a transaction goes after the ones that are, and
 * one without an index or a key image goes before the ones that have it.
 */
class OutputComparator : Comparator<MoneroOutputWallet> {

    override fun compare(o1: MoneroOutputWallet, o2: MoneroOutputWallet): Int {
        val byHeight = compareTxsByHeight(o1.tx, o2.tx)
        if (byHeight != 0) return byHeight
        val byAccount = compareValues(o1.accountIndex, o2.accountIndex)
        if (byAccount != 0) return byAccount
        val bySubaddress = compareValues(o1.subaddressIndex, o2.subaddressIndex)
        if (bySubaddress != 0) return bySubaddress
        val byIndex = compareValues(o1.index, o2.index)
        if (byIndex != 0) return byIndex
        return compareValues(o1.keyImage?.hex, o2.keyImage?.hex)
    }
}
