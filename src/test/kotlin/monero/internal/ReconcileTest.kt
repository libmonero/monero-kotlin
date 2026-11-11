package monero.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import monero.common.MoneroError
import org.junit.jupiter.api.Tag

/**
 * How two values of a field are made one when two copies of the same thing are merged. It mirrors the reconcile tests of monero-python
 * (tests/test_gen_utils.py): the same value on both sides is that value, a side that is not set takes the value of the other, two values
 * that differ are an error, and a field that can change takes the higher or the lower number, or the value that it changes to.
 *
 * Not ported: the choice of returning nothing when only one side is set (resolve_defined), and the preference for the truthy number, which
 * the models do not use.
 */
@Tag("unit")
class ReconcileTest {

    // ----------------------------------- the same value, or a side that is not set -----------------------------------

    @Test
    fun testValueOfTheSameValueIsThatValue() {
        assertEquals(5L, Reconcile.value(5L, 5L, "n"))
        assertNull(Reconcile.value<Long>(null, null, "n"))
        assertEquals("x", Reconcile.value("x", "x", "s"))
        assertEquals(true, Reconcile.value(true, true, "b"))
    }

    @Test
    fun testValueOfASideThatIsNotSetIsTheOther() {
        assertEquals(5L, Reconcile.value(null, 5L, "n"))
        assertEquals(5L, Reconcile.value(5L, null, "n"))
        assertEquals("x", Reconcile.value(null, "x", "s"))
        assertEquals("x", Reconcile.value("x", null, "s"))
        assertEquals(true, Reconcile.value(null, true, "b"))
        assertEquals(false, Reconcile.value(false, null, "b"))
    }

    @Test
    fun testValueOfValuesThatDifferFails() {
        for ((a, b) in listOf<Pair<Any, Any>>(3L to 7L, "a" to "b", true to false)) {
            val error = assertFailsWith<MoneroError>("$a $b") { Reconcile.value(a, b, "the field") }
            assertTrue("Cannot reconcile" in error.message!!, error.message)
            assertTrue("the field" in error.message!!, error.message)
        }
    }

    @Test
    fun testValueOfEqualValuesThatAreNotTheSameObjectIsTheFirst() {
        val a = java.math.BigInteger("100000000000000000000")
        val b = java.math.BigInteger("100000000000000000000")
        assertSame(a, Reconcile.value(a, b, "n"))
    }

    // ----------------------------------- lists -----------------------------------

    @Test
    fun testListOfTheSameListIsThatList() {
        assertEquals(listOf("a", "b"), Reconcile.list(listOf("a", "b"), listOf("a", "b"), "l"))
    }

    @Test
    fun testListOfAListThatIsEmptyOrNotSetIsTheOther() {
        assertEquals(listOf("a", "b"), Reconcile.list(emptyList(), listOf("a", "b"), "l"))
        assertEquals(listOf("a", "b"), Reconcile.list(listOf("a", "b"), emptyList(), "l"))
        assertEquals(listOf("a", "b"), Reconcile.list(null, listOf("a", "b"), "l"))
        assertEquals(listOf("a", "b"), Reconcile.list(listOf("a", "b"), null, "l"))
        assertNull(Reconcile.list<String>(null, null, "l"))
        assertEquals(emptyList(), Reconcile.list(emptyList<String>(), null, "l"))
    }

    @Test
    fun testListOfListsThatDifferFails() {
        val error = assertFailsWith<MoneroError> { Reconcile.list(listOf("a"), listOf("b"), "the list") }
        assertTrue("Cannot reconcile lists" in error.message!!, error.message)
        assertTrue("the list" in error.message!!, error.message)
        assertFailsWith<MoneroError> { Reconcile.list(listOf("a"), listOf("a", "b"), "l") }
    }

    // ----------------------------------- numbers that can change -----------------------------------

    @Test
    fun testMaxIsTheGreater() {
        assertEquals(7L, Reconcile.max(3, 7))
        assertEquals(7L, Reconcile.max(7, 3))
        assertEquals(3L, Reconcile.max(3, 3))
    }

    @Test
    fun testMinIsTheLesser() {
        assertEquals(3L, Reconcile.min(3, 7))
        assertEquals(3L, Reconcile.min(7, 3))
    }

    @Test
    fun testMaxAndMinOfASideThatIsNotSetAreTheOther() {
        assertEquals(5L, Reconcile.max(null, 5))
        assertEquals(5L, Reconcile.max(5, null))
        assertEquals(5L, Reconcile.min(null, 5))
        assertEquals(5L, Reconcile.min(5, null))
        assertNull(Reconcile.max(null, null))
        assertNull(Reconcile.min(null, null))
    }

    // ----------------------------------- flags that can change -----------------------------------

    @Test
    fun testFlagOfValuesThatDifferIsThePreferredOne() {
        assertEquals(true, Reconcile.flag(false, true, true))
        assertEquals(true, Reconcile.flag(true, false, true))
        assertEquals(false, Reconcile.flag(false, true, false))
        assertEquals(false, Reconcile.flag(true, false, false))
    }

    @Test
    fun testFlagOfTheSameValueIsThatValueWhateverIsPreferred() {
        assertEquals(false, Reconcile.flag(false, false, true))
        assertEquals(true, Reconcile.flag(true, true, false))
        assertNull(Reconcile.flag(null, null, true))
    }

    @Test
    fun testFlagOfASideThatIsNotSetIsTheOtherWhateverIsPreferred() {
        assertEquals(false, Reconcile.flag(false, null, true))
        assertEquals(false, Reconcile.flag(null, false, true))
        assertEquals(true, Reconcile.flag(null, true, false))
    }
}
