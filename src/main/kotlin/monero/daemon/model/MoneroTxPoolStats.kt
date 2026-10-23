package monero.daemon.model

import java.math.BigInteger

/**
 * Models transaction pool statistics.
 */
class MoneroTxPoolStats {
    var numTxs: Int? = null
    var numNotRelayed: Int? = null
    var numFailing: Int? = null
    var numDoubleSpends: Int? = null
    // number of transactions in the pool for 10 minutes or more
    var num10m: Int? = null
    var feeTotal: BigInteger? = null
    var bytesMax: Long? = null
    var bytesMed: Long? = null
    var bytesMin: Long? = null
    var bytesTotal: Long? = null
    // histogram of the pool, from a number of bytes to the number of transactions
    var histo: Map<Long, Long>? = null
    // the time that 98% of the transactions are younger than
    var histo98pc: Long? = null
    var oldestTimestamp: Long? = null

    constructor()

    constructor(other: MoneroTxPoolStats) {
        this.numTxs = other.numTxs
        this.numNotRelayed = other.numNotRelayed
        this.numFailing = other.numFailing
        this.numDoubleSpends = other.numDoubleSpends
        this.num10m = other.num10m
        this.feeTotal = other.feeTotal
        this.bytesMax = other.bytesMax
        this.bytesMed = other.bytesMed
        this.bytesMin = other.bytesMin
        this.bytesTotal = other.bytesTotal
        this.histo = other.histo?.let { LinkedHashMap(it) }
        this.histo98pc = other.histo98pc
        this.oldestTimestamp = other.oldestTimestamp
    }

    fun copy(): MoneroTxPoolStats {
        return MoneroTxPoolStats(this)
    }
}
