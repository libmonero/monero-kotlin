package monero.common

import java.math.BigInteger

/**
 * Joins and splits the 128-bit numbers of the daemon, which monero-cpp writes as two 64-bit halves.
 */
internal object MoneroUInt128 {

    private val HALF_MASK = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
    private val MAX = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)

    /**
     * Join the two halves of a 128-bit number.
     *
     * @param low is the least significant 64 bits
     * @param high is the most significant 64 bits
     * @return the number, or null if neither half is set (a half that is missing counts as zero)
     */
    fun combine(low: BigInteger?, high: BigInteger?): BigInteger? {
        if (low == null && high == null) return null
        return (high ?: BigInteger.ZERO).shiftLeft(64).or(low ?: BigInteger.ZERO)
    }

    fun low(value: BigInteger?): BigInteger? = checked(value)?.and(HALF_MASK)

    fun high(value: BigInteger?): BigInteger? = checked(value)?.shiftRight(64)

    private fun checked(value: BigInteger?): BigInteger? {
        if (value != null && (value.signum() < 0 || value > MAX)) throw IllegalArgumentException("$value does not fit in 128 bits")
        return value
    }
}
