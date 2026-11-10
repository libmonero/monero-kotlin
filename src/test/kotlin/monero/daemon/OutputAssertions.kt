package monero.daemon

import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.daemon.model.MoneroKeyImage
import monero.daemon.model.MoneroOutput

/**
 * What the tests of the daemon check in the outputs and the inputs of the transactions that it gives, and in the entries of its output
 * histogram and distribution, like OutputUtils of monero-python.
 */
object OutputAssertions {

    fun testKeyImage(image: MoneroKeyImage?) {
        assertNotNull(image)
        assertTrue(assertNotNull(image.hex).isNotEmpty())
        image.signature?.let { assertTrue(it.isNotEmpty()) }
    }

    /**
     * Check an output of a transaction. With a context, the output has an index and a stealth public key, unless its transaction is in the
     * pool or the context says that the output indices are not there.
     */
    fun testOutput(output: MoneroOutput?, context: TestContext? = null) {
        assertNotNull(output)
        GenAssertions.testUnsignedBigInteger(output.amount)
        if (context == null) return
        val tx = assertNotNull(output.tx)
        if (tx.inTxPool == true || context.hasOutputIndices == false) {
            assertNull(output.index)
        } else {
            assertTrue(assertNotNull(output.index) >= 0)
            assertTrue(assertNotNull(output.stealthPublicKey).isNotEmpty())
        }
    }

    /**
     * Check an input of a transaction: its key image and the indices of its ring.
     */
    fun testInput(input: MoneroOutput?) {
        assertNotNull(input)
        testOutput(input)
        testKeyImage(input.keyImage)
        assertTrue(input.ringOutputIndices.orEmpty().isNotEmpty())
    }
}
