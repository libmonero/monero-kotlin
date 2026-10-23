package monero.daemon.model

/**
 * Result of pruning the blockchain.
 */
class MoneroPruneResult {
    var isPruned: Boolean? = null
    var pruningSeed: Int? = null

    constructor()

    constructor(other: MoneroPruneResult) {
        this.isPruned = other.isPruned
        this.pruningSeed = other.pruningSeed
    }

    fun copy(): MoneroPruneResult {
        return MoneroPruneResult(this)
    }
}
