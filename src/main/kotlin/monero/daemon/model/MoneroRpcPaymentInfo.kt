package monero.daemon.model

/**
 * Models a RPC payment information.
 */
open class MoneroRpcPaymentInfo {
    var credits: Long? = null
    var topBlockHash: String? = null

    constructor()

    constructor(other: MoneroRpcPaymentInfo) {
        this.credits = other.credits
        this.topBlockHash = other.topBlockHash
    }

    open fun copy(): MoneroRpcPaymentInfo {
        return MoneroRpcPaymentInfo(this)
    }
}
