package monero.daemon

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The checks of numbers that the tests of the daemon share, like GenUtils of monero-python.
 */
object GenAssertions {

    /**
     * Check an unsigned number, such as an amount or a credit: it is there and it is not negative, and it is not zero or it is zero if the
     * test says so.
     *
     * @param num is the number to check: an amount in a BigInteger or a count in a Long
     * @param nonZero is true if the number has to be greater than zero, false if it has to be zero, and null if it can be either
     */
    fun testUnsignedBigInteger(num: Number?, nonZero: Boolean? = null) {
        assertNotNull(num, "Number is null")
        val value = if (num is BigInteger) num else BigInteger(num.toString())
        assertTrue(value.signum() >= 0, "Value cannot be negative: $value")
        when (nonZero) {
            true -> assertTrue(value.signum() > 0, "Number is zero")
            false -> assertEquals(0, value.signum(), "Number is not zero: $value")
            null -> {}
        }
    }
}
