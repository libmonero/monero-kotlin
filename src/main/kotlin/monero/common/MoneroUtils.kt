package monero.common

import com.sun.jna.ptr.LongByReference
import java.math.BigInteger
import monero.internal.MoneroNative

/**
 * Collection of Monero utilities. They are run by monero-c, which is loaded on the first call.
 */
object MoneroUtils {

    private val lib get() = MoneroNative.lib

    /**
     * Convert XMR to atomic units. 1 XMR is 10^12 atomic units, and the amount is rounded to the nearest one.
     *
     * @param amountXmr is the amount in XMR to convert
     * @return the amount in atomic units
     * @throws MoneroError if the amount is negative, not finite, or too big for an unsigned 64-bit number
     */
    fun xmrToAtomicUnits(amountXmr: Double): BigInteger {
        val out = LongByReference()
        MoneroNative.ok(lib.monero_utils_xmr_to_atomic_units(amountXmr, out))
        return MoneroNative.unsigned(out.value)
    }

    /**
     * Convert atomic units to XMR.
     *
     * @param atomicUnits is the amount in atomic units to convert
     * @return the amount in XMR
     * @throws MoneroError if the amount is negative, or too big for an unsigned 64-bit number
     */
    fun atomicUnitsToXmr(atomicUnits: BigInteger): Double {
        if (atomicUnits.signum() < 0 || atomicUnits.bitLength() > 64) {
            throw MoneroError("The amount in atomic units is not an unsigned 64-bit number: $atomicUnits")
        }
        return lib.monero_utils_atomic_units_to_xmr(atomicUnits.toLong())
    }
}
