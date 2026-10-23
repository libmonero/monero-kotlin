package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import monero.common.MoneroUInt128

/**
 * Models a Monero block header which contains information about the block.
 */
open class MoneroBlockHeader {
    var hash: String? = null
    var height: Long? = null
    var timestamp: Long? = null
    var size: Long? = null
    var weight: Long? = null
    var longTermWeight: Long? = null
    var depth: Long? = null
    var difficultyLow: BigInteger? = null
    var difficultyHigh: BigInteger? = null

    /**
     * Get or set the block difficulty, which has 128 bits: it is joined from its two halves, and setting it sets both.
     */
    @get:JsonIgnore
    var difficulty: BigInteger?
        get() = MoneroUInt128.combine(difficultyLow, difficultyHigh)
        set(value) {
            difficultyLow = MoneroUInt128.low(value)
            difficultyHigh = MoneroUInt128.high(value)
        }
    var cumulativeDifficultyLow: BigInteger? = null
    var cumulativeDifficultyHigh: BigInteger? = null

    /**
     * Get or set the block's cumulative difficulty, which has 128 bits: it is joined from its two halves, and setting it sets both.
     */
    @get:JsonIgnore
    var cumulativeDifficulty: BigInteger?
        get() = MoneroUInt128.combine(cumulativeDifficultyLow, cumulativeDifficultyHigh)
        set(value) {
            cumulativeDifficultyLow = MoneroUInt128.low(value)
            cumulativeDifficultyHigh = MoneroUInt128.high(value)
        }
    var majorVersion: Int? = null
    var minorVersion: Int? = null
    var nonce: Long? = null
    var minerTxHash: String? = null
    var numTxs: Int? = null
    var orphanStatus: Boolean? = null
    var prevHash: String? = null
    var reward: BigInteger? = null
    var powHash: String? = null

    constructor()

    constructor(other: MoneroBlockHeader) {
        this.hash = other.hash
        this.height = other.height
        this.timestamp = other.timestamp
        this.size = other.size
        this.weight = other.weight
        this.longTermWeight = other.longTermWeight
        this.depth = other.depth
        this.difficultyLow = other.difficultyLow
        this.difficultyHigh = other.difficultyHigh
        this.cumulativeDifficultyLow = other.cumulativeDifficultyLow
        this.cumulativeDifficultyHigh = other.cumulativeDifficultyHigh
        this.majorVersion = other.majorVersion
        this.minorVersion = other.minorVersion
        this.nonce = other.nonce
        this.minerTxHash = other.minerTxHash
        this.numTxs = other.numTxs
        this.orphanStatus = other.orphanStatus
        this.prevHash = other.prevHash
        this.reward = other.reward
        this.powHash = other.powHash
    }

    open fun copy(): MoneroBlockHeader {
        return MoneroBlockHeader(this)
    }
}
