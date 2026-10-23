package monero.wallet.model

import java.math.BigInteger

/**
 * Models a destination of an outgoing transfer: an address and an amount.
 */
class MoneroDestination {
    var address: String? = null
    var amount: BigInteger? = null

    constructor()

    constructor(other: MoneroDestination) {
        this.address = other.address
        this.amount = other.amount
    }

    constructor(address: String?, amount: BigInteger? = null) {
        this.address = address
        this.amount = amount
    }

    fun copy(): MoneroDestination {
        return MoneroDestination(this)
    }
}
