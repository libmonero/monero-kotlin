package monero.wallet.model

import java.math.BigInteger

/**
 * Models the result of checking a proof of the reserve of a wallet.
 */
class MoneroCheckReserve : MoneroCheck {
    var totalAmount: BigInteger? = null
    var unconfirmedSpentAmount: BigInteger? = null

    constructor() : super()

    constructor(other: MoneroCheckReserve) : super(other) {
        this.totalAmount = other.totalAmount
        this.unconfirmedSpentAmount = other.unconfirmedSpentAmount
    }

    override fun copy(): MoneroCheckReserve {
        return MoneroCheckReserve(this)
    }
}
