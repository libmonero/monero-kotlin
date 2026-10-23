package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import monero.common.MoneroUInt128

/**
 * Models an alternative chain seen by the node.
 */
class MoneroAltChain {
    var blockHashes: List<String>? = null
    var difficultyLow: BigInteger? = null
    var difficultyHigh: BigInteger? = null

    /**
     * Get or set the cumulative difficulty of the alternative chain, which has 128 bits: it is joined from its two halves, and setting it sets both.
     */
    @get:JsonIgnore
    var difficulty: BigInteger?
        get() = MoneroUInt128.combine(difficultyLow, difficultyHigh)
        set(value) {
            difficultyLow = MoneroUInt128.low(value)
            difficultyHigh = MoneroUInt128.high(value)
        }
    var length: Long? = null
    var height: Long? = null
    var mainChainParentBlockHash: String? = null

    constructor()

    constructor(other: MoneroAltChain) {
        this.blockHashes = other.blockHashes?.let { ArrayList(it) }
        this.difficultyLow = other.difficultyLow
        this.difficultyHigh = other.difficultyHigh
        this.length = other.length
        this.height = other.height
        this.mainChainParentBlockHash = other.mainChainParentBlockHash
    }

    fun copy(): MoneroAltChain {
        return MoneroAltChain(this)
    }
}
