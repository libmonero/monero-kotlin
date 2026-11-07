package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import monero.common.MoneroUInt128
import monero.internal.Reconcile

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

    /**
     * Merge another header of the same block into this one: a field that is set on one side is taken, and the same value on both is
     * kept. The height and the timestamp are the higher of the two, as a header gets them from the chain as it grows. A MoneroBlock
     * merges the rest of the block too.
     *
     * @param other is the header to merge into this one
     * @throws MoneroError if a field that does not change has a different value in the two
     */
    open fun merge(other: MoneroBlockHeader) {
        if (this === other) return
        hash = Reconcile.value(hash, other.hash, "block header hash")
        height = Reconcile.max(height, other.height)
        timestamp = Reconcile.max(timestamp, other.timestamp)
        size = Reconcile.value(size, other.size, "block header size")
        weight = Reconcile.value(weight, other.weight, "block header weight")
        longTermWeight = Reconcile.value(longTermWeight, other.longTermWeight, "block header longTermWeight")
        depth = Reconcile.value(depth, other.depth, "block header depth")
        difficultyLow = Reconcile.value(difficultyLow, other.difficultyLow, "block header difficultyLow")
        difficultyHigh = Reconcile.value(difficultyHigh, other.difficultyHigh, "block header difficultyHigh")
        cumulativeDifficultyLow = Reconcile.value(cumulativeDifficultyLow, other.cumulativeDifficultyLow, "block header cumulativeDifficultyLow")
        cumulativeDifficultyHigh = Reconcile.value(cumulativeDifficultyHigh, other.cumulativeDifficultyHigh, "block header cumulativeDifficultyHigh")
        majorVersion = Reconcile.value(majorVersion, other.majorVersion, "block header majorVersion")
        minorVersion = Reconcile.value(minorVersion, other.minorVersion, "block header minorVersion")
        nonce = Reconcile.value(nonce, other.nonce, "block header nonce")
        minerTxHash = Reconcile.value(minerTxHash, other.minerTxHash, "block header minerTxHash")
        numTxs = Reconcile.value(numTxs, other.numTxs, "block header numTxs")
        orphanStatus = Reconcile.value(orphanStatus, other.orphanStatus, "block header orphanStatus")
        prevHash = Reconcile.value(prevHash, other.prevHash, "block header prevHash")
        reward = Reconcile.value(reward, other.reward, "block header reward")
        powHash = Reconcile.value(powHash, other.powHash, "block header powHash")
    }
}
