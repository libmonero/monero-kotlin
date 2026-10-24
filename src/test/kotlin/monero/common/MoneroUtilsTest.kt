package monero.common

import java.math.BigInteger
import monero.NativeTestSupport
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Monero utilities unit tests.
 */
class MoneroUtilsTest {

    @BeforeTest
    fun requireLibrary() {
        NativeTestSupport.assumeAvailable()
    }

    // Can convert between XMR and atomic units
    @Test
    fun testAtomicUnitConversion() {
        assertEquals(BigInteger.valueOf(1000000000000L), MoneroUtils.xmrToAtomicUnits(1.0))
        assertEquals(1.0, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(1000000000000L)))
        assertEquals(BigInteger.valueOf(1000000000L), MoneroUtils.xmrToAtomicUnits(0.001))
        assertEquals(0.001, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(1000000000L)))
        assertEquals(BigInteger.valueOf(250000000000L), MoneroUtils.xmrToAtomicUnits(0.25))
        assertEquals(0.25, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(250000000000L)))
        assertEquals(BigInteger.valueOf(1250000000000L), MoneroUtils.xmrToAtomicUnits(1.25))
        assertEquals(1.25, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(1250000000000L)))
        assertEquals(BigInteger.valueOf(2796726190000L), MoneroUtils.xmrToAtomicUnits(2.79672619))
        assertEquals(2.79672619, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(2796726190000L)))
        assertEquals(BigInteger.valueOf(2796726190001L), MoneroUtils.xmrToAtomicUnits(2.796726190001))
        assertEquals(2.796726190001, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(2796726190001L)))
        assertEquals(BigInteger.valueOf(2796726189999L), MoneroUtils.xmrToAtomicUnits(2.796726189999))
        assertEquals(2.796726189999, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(2796726189999L)))
        assertEquals(BigInteger.valueOf(2796726180000L), MoneroUtils.xmrToAtomicUnits(2.79672618))
        assertEquals(2.79672618, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(2796726180000L)))
    }

}
