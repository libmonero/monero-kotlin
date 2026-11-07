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
    fun value_OfTheSameValue_IsThatValue() {
        assertEquals(5L, Reconcile.value(5L, 5L, "n"))
        assertNull(Reconcile.value<Long>(null, null, "n"))
        assertEquals("x", Reconcile.value("x", "x", "s"))
        assertEquals(true, Reconcile.value(true, true, "b"))
    }

    @Test
    fun value_OfASideThatIsNotSet_IsTheOther() {
        assertEquals(5L, Reconcile.value(null, 5L, "n"))
        assertEquals(5L, Reconcile.value(5L, null, "n"))
        assertEquals("x", Reconcile.value(null, "x", "s"))
        assertEquals("x", Reconcile.value("x", null, "s"))
        assertEquals(true, Reconcile.value(null, true, "b"))
        assertEquals(false, Reconcile.value(false, null, "b"))
    }

    @Test
    fun value_OfValuesThatDiffer_Fails() {
        for ((a, b) in listOf<Pair<Any, Any>>(3L to 7L, "a" to "b", true to false)) {
            val error = assertFailsWith<MoneroError>("$a $b") { Reconcile.value(a, b, "the field") }
            assertTrue("Cannot reconcile" in error.message!!, error.message)
            assertTrue("the field" in error.message!!, error.message)
        }
    }

    @Test
    fun value_OfEqualValuesThatAreNotTheSameObject_IsTheFirst() {
        val a = java.math.BigInteger("100000000000000000000")
        val b = java.math.BigInteger("100000000000000000000")
        assertSame(a, Reconcile.value(a, b, "n"))
    }

    // ----------------------------------- lists -----------------------------------

    @Test
    fun list_OfTheSameList_IsThatList() {
        assertEquals(listOf("a", "b"), Reconcile.list(listOf("a", "b"), listOf("a", "b"), "l"))
    }

    @Test
    fun list_OfAListThatIsEmptyOrNotSet_IsTheOther() {
        assertEquals(listOf("a", "b"), Reconcile.list(emptyList(), listOf("a", "b"), "l"))
        assertEquals(listOf("a", "b"), Reconcile.list(listOf("a", "b"), emptyList(), "l"))
        assertEquals(listOf("a", "b"), Reconcile.list(null, listOf("a", "b"), "l"))
        assertEquals(listOf("a", "b"), Reconcile.list(listOf("a", "b"), null, "l"))
        assertNull(Reconcile.list<String>(null, null, "l"))
        assertEquals(emptyList(), Reconcile.list(emptyList<String>(), null, "l"))
    }

    @Test
    fun list_OfListsThatDiffer_Fails() {
        val error = assertFailsWith<MoneroError> { Reconcile.list(listOf("a"), listOf("b"), "the list") }
        assertTrue("Cannot reconcile lists" in error.message!!, error.message)
        assertTrue("the list" in error.message!!, error.message)
        assertFailsWith<MoneroError> { Reconcile.list(listOf("a"), listOf("a", "b"), "l") }
    }

    // ----------------------------------- numbers that can change -----------------------------------

    @Test
    fun max_IsTheGreater() {
        assertEquals(7L, Reconcile.max(3, 7))
        assertEquals(7L, Reconcile.max(7, 3))
        assertEquals(3L, Reconcile.max(3, 3))
    }

    @Test
    fun min_IsTheLesser() {
        assertEquals(3L, Reconcile.min(3, 7))
        assertEquals(3L, Reconcile.min(7, 3))
    }

    @Test
    fun maxAndMin_OfASideThatIsNotSet_AreTheOther() {
        assertEquals(5L, Reconcile.max(null, 5))
        assertEquals(5L, Reconcile.max(5, null))
        assertEquals(5L, Reconcile.min(null, 5))
        assertEquals(5L, Reconcile.min(5, null))
        assertNull(Reconcile.max(null, null))
        assertNull(Reconcile.min(null, null))
    }

    // ----------------------------------- flags that can change -----------------------------------

    @Test
    fun flag_OfValuesThatDiffer_IsThePreferredOne() {
        assertEquals(true, Reconcile.flag(false, true, true))
        assertEquals(true, Reconcile.flag(true, false, true))
        assertEquals(false, Reconcile.flag(false, true, false))
        assertEquals(false, Reconcile.flag(true, false, false))
    }

    @Test
    fun flag_OfTheSameValue_IsThatValue_WhateverIsPreferred() {
        assertEquals(false, Reconcile.flag(false, false, true))
        assertEquals(true, Reconcile.flag(true, true, false))
        assertNull(Reconcile.flag(null, null, true))
    }

    @Test
    fun flag_OfASideThatIsNotSet_IsTheOther_WhateverIsPreferred() {
        assertEquals(false, Reconcile.flag(false, null, true))
        assertEquals(false, Reconcile.flag(null, false, true))
        assertEquals(true, Reconcile.flag(null, true, false))
    }
}
