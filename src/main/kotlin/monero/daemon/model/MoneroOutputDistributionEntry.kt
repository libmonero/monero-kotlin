package monero.daemon.model

import java.math.BigInteger

/**
 * Monero output distribution entry.
 */
class MoneroOutputDistributionEntry {
    var amount: BigInteger? = null
    // total number of outputs of the amount in the chain before the block at startHeight
    var base: Long? = null
    var distribution: List<Long>? = null
    // not necessarily equal to the requested height, especially for an amount of 0
    var startHeight: Long? = null

    constructor()

    constructor(other: MoneroOutputDistributionEntry) {
        this.amount = other.amount
        this.base = other.base
        this.distribution = other.distribution?.let { ArrayList(it) }
        this.startHeight = other.startHeight
    }

    fun copy(): MoneroOutputDistributionEntry {
        return MoneroOutputDistributionEntry(this)
    }
}
