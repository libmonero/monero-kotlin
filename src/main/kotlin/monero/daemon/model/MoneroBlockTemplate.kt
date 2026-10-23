package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import monero.common.MoneroUInt128

/**
 * Monero block template to mine.
 */
class MoneroBlockTemplate {
    // blob on which to try to mine a new block
    var blockTemplateBlob: String? = null
    // blob on which to try to find a valid nonce
    var blockHashingBlob: String? = null
    var difficultyLow: BigInteger? = null
    var difficultyHigh: BigInteger? = null

    /**
     * Get or set the network difficulty, which has 128 bits: it is joined from its two halves, and setting it sets both.
     */
    @get:JsonIgnore
    var difficulty: BigInteger?
        get() = MoneroUInt128.combine(difficultyLow, difficultyHigh)
        set(value) {
            difficultyLow = MoneroUInt128.low(value)
            difficultyHigh = MoneroUInt128.high(value)
        }
    // coinbase reward expected to be received if the block is successfully mined
    var expectedReward: BigInteger? = null
    // height on which to mine
    var height: Long? = null
    var prevHash: String? = null
    var reservedOffset: Long? = null
    // height of the block to use as seed for Random-X proof-of-work
    var seedHeight: Long? = null
    var seedHash: String? = null
    var nextSeedHash: String? = null

    constructor()

    constructor(other: MoneroBlockTemplate) {
        this.blockTemplateBlob = other.blockTemplateBlob
        this.blockHashingBlob = other.blockHashingBlob
        this.difficultyLow = other.difficultyLow
        this.difficultyHigh = other.difficultyHigh
        this.expectedReward = other.expectedReward
        this.height = other.height
        this.prevHash = other.prevHash
        this.reservedOffset = other.reservedOffset
        this.seedHeight = other.seedHeight
        this.seedHash = other.seedHash
        this.nextSeedHash = other.nextSeedHash
    }

    fun copy(): MoneroBlockTemplate {
        return MoneroBlockTemplate(this)
    }
}
