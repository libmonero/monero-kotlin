package monero.daemon

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.daemon.model.MoneroMinerTxSum
import monero.daemon.model.MoneroSubmitTxResult

/**
 * What the tests of the daemon check in what it answers, like DaemonUtils of monero-python.
 */
object DaemonAssertions {

    /**
     * Check the sum of the emission and of the fees of the miner transactions of a range of blocks.
     */
    fun testMinerTxSum(txSum: MoneroMinerTxSum) {
        GenAssertions.testUnsignedBigInteger(txSum.emissionSumLow)
        GenAssertions.testUnsignedBigInteger(txSum.emissionSumHigh)
        GenAssertions.testUnsignedBigInteger(txSum.feeSumLow)
        GenAssertions.testUnsignedBigInteger(txSum.feeSumHigh)
    }

    /**
     * Check what every result of the submission of a transaction has.
     */
    fun testSubmitTxResultCommon(result: MoneroSubmitTxResult) {
        assertNotNull(result.isGood)
        assertNotNull(result.isRelayed)
        assertNotNull(result.isDoubleSpend)
        assertNotNull(result.isFeeTooLow)
        assertNotNull(result.isMixinTooLow)
        assertNotNull(result.hasInvalidInput)
        assertNotNull(result.hasInvalidOutput)
        assertNotNull(result.isOverspend)
        assertNotNull(result.isTooBig)
        assertNotNull(result.sanityCheckFailed)
        result.reason?.let { assertTrue(it.isNotEmpty()) }
    }

    /**
     * Check the result of the submission of a transaction that the daemon took.
     */
    fun testSubmitTxResultGood(result: MoneroSubmitTxResult) {
        testSubmitTxResultCommon(result)
        assertEquals(false, result.isDoubleSpend, "tx submission is double spend.")
        assertEquals(false, result.isFeeTooLow, "fee is too low.")
        assertEquals(false, result.isMixinTooLow, "mixin is too low.")
        assertEquals(false, result.hasInvalidInput, "tx has invalid input.")
        assertEquals(false, result.hasInvalidOutput, "tx has invalid output.")
        assertEquals(false, result.hasTooFewOutputs, "tx has too few outputs.")
        assertEquals(false, result.isOverspend, "tx is overspend.")
        assertEquals(false, result.isTooBig, "tx is too big.")
        assertEquals(false, result.sanityCheckFailed, "tx sanity check failed.")

        // 0 credits
        GenAssertions.testUnsignedBigInteger(result.credits, false)
        assertNull(result.topBlockHash)
        assertEquals(false, result.isTxExtraTooBig, "tx extra is too big.")
        assertEquals(true, result.isGood)
        assertEquals(false, result.isNonZeroUnlockTime, "tx has non-zero unlock time.")
    }

    /**
     * Get the hashes of the confirmed transactions of the last blocks, until it has five, from the last block to the first one.
     */
    fun getConfirmedTxHashes(daemon: MoneroDaemon): MutableList<String> {
        val hashes = ArrayList<String>()
        var height = daemon.getHeight()
        while (hashes.size < 5 && height > 0) {
            height--
            hashes.addAll(daemon.getBlockByHeight(height).txHashes.orEmpty())
        }
        return hashes
    }
}
