package monero.daemon.model

import java.math.BigInteger

/**
 * Models an estimation on fees per byte.
 */
class MoneroFeeEstimate {
    // fee per byte estimation (atomic units)
    var fee: BigInteger? = null
    // default fees ordered by transaction priority
    var fees: List<BigInteger>? = null
    // the final fee estimation should be rounded up to an even multiple of this value
    var quantizationMask: Long? = null

    constructor()

    constructor(other: MoneroFeeEstimate) {
        this.fee = other.fee
        this.fees = other.fees?.let { ArrayList(it) }
        this.quantizationMask = other.quantizationMask
    }

    fun copy(): MoneroFeeEstimate {
        return MoneroFeeEstimate(this)
    }
}
