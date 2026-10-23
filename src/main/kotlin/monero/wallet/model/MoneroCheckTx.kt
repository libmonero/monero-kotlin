package monero.wallet.model

import java.math.BigInteger

/**
 * Models the result of checking a transaction with its key, or with a proof of it.
 */
class MoneroCheckTx : MoneroCheck {
    var inTxPool: Boolean? = null
    var numConfirmations: Long? = null
    var receivedAmount: BigInteger? = null

    constructor() : super()

    constructor(other: MoneroCheckTx) : super(other) {
        this.inTxPool = other.inTxPool
        this.numConfirmations = other.numConfirmations
        this.receivedAmount = other.receivedAmount
    }

    override fun copy(): MoneroCheckTx {
        return MoneroCheckTx(this)
    }
}
