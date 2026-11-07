package monero

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail
import monero.common.MoneroError
import org.opentest4j.AssertionFailedError
import org.opentest4j.TestAbortedException

/**
 * The marks [NotSupported] and [NotImplemented]. The functions of [TestMarkers] are called as they are, and two tests of the
 * class carry the marks themselves, so that the run fails if [TestMarkersExtension] is not on.
 */
class TestMarkersTest {

    @Test
    fun expectNotSupported_PassesWhenTheErrorSaysIt() {
        TestMarkers.expectNotSupported("a") { throw MoneroError("Not supported") }
        TestMarkers.expectNotSupported("b") { throw MoneroError("The keys wallet does not support saving") }
        TestMarkers.expectNotSupported("c") { throw UnsupportedOperationException("it doesn't support that") }
    }

    @Test
    fun expectNotSupported_ThrowsAnyOtherErrorAsItIs() {
        val error = MoneroError("The wallet is closed")
        assertSame(error, assertFailsWith<MoneroError> { TestMarkers.expectNotSupported("a") { throw error } })
    }

    @Test
    fun expectNotSupported_FailsWhenThereIsNoError() {
        assertFailsWith<AssertionFailedError> { TestMarkers.expectNotSupported("a") { } }
    }

    @Test
    fun expectNotSupported_LetsAnAssertionFailureThrough() {
        // a check that fails is not the error of an operation that is not supported
        assertFailsWith<AssertionFailedError> { TestMarkers.expectNotSupported("a") { throw AssertionFailedError("not supported") } }
    }

    @Test
    fun expectNotSupported_KeepsTheSkipOfAnAssumption() {
        val skip = TestAbortedException("no library")
        assertSame(skip, assertFailsWith<TestAbortedException> { TestMarkers.expectNotSupported("a") { throw skip } })
    }

    @Test
    fun expectNotImplemented_SkipsWhileTheTestFails() {
        val error = MoneroError("not there")
        val skip = assertFailsWith<TestAbortedException> { TestMarkers.expectNotImplemented("a") { throw error } }
        assertSame(error, skip.cause)
        assertTrue(skip.message!!.contains("not there"))
        assertFailsWith<TestAbortedException> { TestMarkers.expectNotImplemented("b") { assertEquals(1, 2) } }
    }

    @Test
    fun expectNotImplemented_PassesOnceTheTestPasses() {
        var ran = false
        TestMarkers.expectNotImplemented("a") { ran = true }
        assertTrue(ran)
    }

    @Test
    fun expectNotImplemented_KeepsTheSkipOfAnAssumption() {
        val skip = TestAbortedException("no library")
        assertSame(skip, assertFailsWith<TestAbortedException> { TestMarkers.expectNotImplemented("a") { throw skip } })
    }

    @Test
    fun expectNotImplemented_DoesNotHideAnErrorOfTheJvm() {
        assertFailsWith<StackOverflowError> { TestMarkers.expectNotImplemented("a") { throw StackOverflowError() } }
    }

    // passes if the extension is on: it throws what the mark expects
    @NotSupported
    @Test
    fun marked_NotSupported_PassesWhenItThrowsNotSupported() {
        throw MoneroError("Not supported")
    }

    // is reported as skipped if the extension is on, and fails if it is not
    @NotImplemented
    @Test
    fun marked_NotImplemented_IsSkippedWhileItFails() {
        fail("This is not implemented")
    }
}
