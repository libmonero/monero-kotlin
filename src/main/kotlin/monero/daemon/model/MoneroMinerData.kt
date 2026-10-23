package monero.daemon.model

import java.math.BigInteger

/**
 * Models the data that a miner needs to build a block.
 */
class MoneroMinerData : MoneroRpcPaymentInfo {
    var majorVersion: Int? = null
    var height: Long? = null
    var prevHash: String? = null
    var seedHash: String? = null
    // difficulty of the next block, in hexadecimal format as the daemon writes it
    var difficulty: String? = null
    var medianWeight: Long? = null
    // coins emitted so far (atomic units)
    var alreadyGeneratedCoins: BigInteger? = null
    // the transactions of the pool that a block can take, with their fees and weights
    var txPoolBacklog: List<MoneroTx>? = null

    constructor() : super()

    constructor(other: MoneroMinerData) : super(other) {
        this.majorVersion = other.majorVersion
        this.height = other.height
        this.prevHash = other.prevHash
        this.seedHash = other.seedHash
        this.difficulty = other.difficulty
        this.medianWeight = other.medianWeight
        this.alreadyGeneratedCoins = other.alreadyGeneratedCoins
        this.txPoolBacklog = other.txPoolBacklog?.map { it.copy() }
    }

    override fun copy(): MoneroMinerData {
        return MoneroMinerData(this)
    }
}
