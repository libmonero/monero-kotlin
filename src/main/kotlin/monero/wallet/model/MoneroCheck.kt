package monero.wallet.model

/**
 * Base class of the results of checking a proof of a transaction or of a reserve.
 */
open class MoneroCheck {
    var isGood: Boolean? = null

    constructor()

    constructor(other: MoneroCheck) {
        this.isGood = other.isGood
    }

    open fun copy(): MoneroCheck {
        return MoneroCheck(this)
    }
}
