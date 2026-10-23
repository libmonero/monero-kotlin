package monero.daemon.model

import java.math.BigInteger

/**
 * Entry in a Monero output histogram (see get_output_histogram of Daemon RPC documentation).
 */
class MoneroOutputHistogramEntry {
    var amount: BigInteger? = null
    var numInstances: Long? = null
    var unlockedInstances: Long? = null
    var recentInstances: Long? = null

    constructor()

    constructor(other: MoneroOutputHistogramEntry) {
        this.amount = other.amount
        this.numInstances = other.numInstances
        this.unlockedInstances = other.unlockedInstances
        this.recentInstances = other.recentInstances
    }

    fun copy(): MoneroOutputHistogramEntry {
        return MoneroOutputHistogramEntry(this)
    }
}
