package monero.daemon

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.daemon.model.MoneroKeyImageSpentStatus
import monero.daemon.model.MoneroMinerTxSum
import monero.daemon.model.MoneroSubmitTxResult
import monero.daemon.model.MoneroTx
import monero.daemon.model.MoneroTxPoolStats

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
     * Check the statistics of the transaction pool of the daemon: the sizes, the times and the counts that a pool with transactions has,
     * and the zeros and the nulls of an empty pool.
     */
    fun testTxPoolStats(stats: MoneroTxPoolStats) {
        val numTxs = assertNotNull(stats.numTxs)
        assertTrue(numTxs >= 0)
        if (numTxs > 0) {
            assertTrue(assertNotNull(stats.bytesMax) > 0)
            assertTrue(assertNotNull(stats.bytesMed) > 0)
            assertTrue(assertNotNull(stats.bytesMin) > 0)
            assertTrue(assertNotNull(stats.bytesTotal) > 0)
            assertTrue(assertNotNull(stats.oldestTimestamp) > 0)
            stats.histo98pc?.let { assertTrue(it > 0) }
            assertTrue(assertNotNull(stats.num10m) >= 0)
            assertTrue(assertNotNull(stats.numDoubleSpends) >= 0)
            assertTrue(assertNotNull(stats.numFailing) >= 0)
            assertTrue(assertNotNull(stats.numNotRelayed) >= 0)
        } else {
            assertNull(stats.bytesMax)
            assertNull(stats.bytesMed)
            assertNull(stats.bytesMin)
            assertEquals(0L, stats.bytesTotal)
            assertNull(stats.histo98pc)
            assertNull(stats.oldestTimestamp)
            assertEquals(0, stats.num10m)
            assertEquals(0, stats.numDoubleSpends)
            assertEquals(0, stats.numFailing)
            assertEquals(0, stats.numNotRelayed)
            assertTrue(stats.histo.orEmpty().isEmpty())
        }
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
     * Check that the daemon gives the status that is expected for each key image, one by one and all together.
     */
    fun testSpentStatuses(daemon: MoneroDaemon, keyImages: List<String>, expectedStatus: MoneroKeyImageSpentStatus) {
        // one image
        for (keyImage in keyImages) assertEquals(expectedStatus, daemon.getKeyImageSpentStatus(keyImage))

        // a list of images
        val statuses = if (keyImages.isNotEmpty()) daemon.getKeyImageSpentStatuses(keyImages) else emptyList()
        assertEquals(keyImages.size, statuses.size)
        for (status in statuses) assertEquals(expectedStatus, status)
    }

    /**
     * Get confirmed transactions of the chain, from the last blocks to the first ones.
     *
     * @param daemon is the daemon to ask
     * @param numTxs is the number of transactions to get
     * @throws AssertionError if the chain has less transactions than that
     */
    fun getConfirmedTxs(daemon: MoneroDaemon, numTxs: Int): List<MoneroTx> {
        val txs = ArrayList<MoneroTx>()
        val numBlocksPerRequest = 50L
        var startIdx = daemon.getHeight() - numBlocksPerRequest - 1

        while (startIdx >= 0) {
            for (block in daemon.getBlocksByRange(startIdx, startIdx + numBlocksPerRequest)) {
                for (tx in block.txs.orEmpty()) {
                    txs.add(tx)
                    if (txs.size == numTxs) return txs
                }
            }
            startIdx -= numBlocksPerRequest
        }

        throw AssertionError("Could not get $numTxs confirmed txs (found: ${txs.size})")
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
