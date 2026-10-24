package monero.wallet.lws.model

import java.math.BigInteger

// a transaction as the light wallet shows it. the actual amount is not
// a guess with the view key: an incoming tx is its total_received, an
// outgoing one its total_sent + fee
class MoneroLwsTransaction {
    var hash: String? = null
    var actualAmount: BigInteger? = null
    var isIncoming: Boolean? = null
    var timestamp: Long? = null        // epoch millis
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
