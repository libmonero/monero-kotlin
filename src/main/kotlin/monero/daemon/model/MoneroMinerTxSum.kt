package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import monero.common.MoneroUInt128

/**
 * Model for the summation of miner emissions and fees.
 */
class MoneroMinerTxSum {
    var emissionSumLow: BigInteger? = null
    var emissionSumHigh: BigInteger? = null

    /**
     * Get or set the sum of the new coins emitted (atomic units), which has 128 bits: it is joined from its two halves, and setting it sets both.
     */
    @get:JsonIgnore
    var emissionSum: BigInteger?
        get() = MoneroUInt128.combine(emissionSumLow, emissionSumHigh)
        set(value) {
            emissionSumLow = MoneroUInt128.low(value)
            emissionSumHigh = MoneroUInt128.high(value)
        }
    var feeSumLow: BigInteger? = null
    var feeSumHigh: BigInteger? = null

    /**
     * Get or set the sum of the fees (atomic units), which has 128 bits: it is joined from its two halves, and setting it sets both.
     */
    @get:JsonIgnore
    var feeSum: BigInteger?
        get() = MoneroUInt128.combine(feeSumLow, feeSumHigh)
        set(value) {
            feeSumLow = MoneroUInt128.low(value)
            feeSumHigh = MoneroUInt128.high(value)
        }

    constructor()

    constructor(other: MoneroMinerTxSum) {
        this.emissionSumLow = other.emissionSumLow
        this.emissionSumHigh = other.emissionSumHigh
        this.feeSumLow = other.feeSumLow
        this.feeSumHigh = other.feeSumHigh
    }

    fun copy(): MoneroMinerTxSum {
        return MoneroMinerTxSum(this)
    }
}
