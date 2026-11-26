package monero.wallet.model

import com.fasterxml.jackson.annotation.JsonSetter

/**
 * Groups the transactions that a wallet made with the hex that signs or relays them. The hex is in the set and not in
 * the transactions: a view-only wallet makes an unsigned set, the wallet that has the keys signs it, and the wallet
 * that relays it takes the signed one.
 */
class MoneroTxSet {

    /**
     * Transactions of the set. Assigning the list sets the set of each transaction.
     */
    @set:JsonSetter("txs")
    var txs: List<MoneroTxWallet>? = null
        set(value) {
            field = value
            value?.forEach { it.txSet = this }
        }
    var signedTxHex: String? = null
    var unsignedTxHex: String? = null
    var multisigTxHex: String? = null

    constructor()

    constructor(txSet: MoneroTxSet) {
        this.txs = txSet.txs?.map { it.copy() }
        this.signedTxHex = txSet.signedTxHex
        this.unsignedTxHex = txSet.unsignedTxHex
        this.multisigTxHex = txSet.multisigTxHex
    }

    fun copy(): MoneroTxSet {
        return MoneroTxSet(this)
    }
}
