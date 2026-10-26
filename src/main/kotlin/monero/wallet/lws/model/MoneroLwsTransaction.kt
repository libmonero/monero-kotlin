package monero.wallet.lws.model

import java.math.BigInteger

/**
 * Models a transaction as the light wallet shows it. The actual amount is not a guess with the view key: an incoming
 * transaction is what it received, and an outgoing one what it sent plus its fee.
 */
class MoneroLwsTransaction {
    var hash: String? = null
    var actualAmount: BigInteger? = null
    var isIncoming: Boolean? = null
    // the time of the transaction, in epoch milliseconds
    var timestamp: Long? = null
    var height: Long? = null

    constructor()

    constructor(other: MoneroLwsTransaction) {
        this.hash = other.hash
        this.actualAmount = other.actualAmount
        this.isIncoming = other.isIncoming
        this.timestamp = other.timestamp
        this.height = other.height
    }

    fun copy(): MoneroLwsTransaction {
        return MoneroLwsTransaction(this)
    }
}
