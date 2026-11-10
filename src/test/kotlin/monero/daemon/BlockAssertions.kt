package monero.daemon

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import monero.TestLog
import monero.daemon.model.MoneroAltChain
import monero.daemon.model.MoneroBlock
import monero.daemon.model.MoneroBlockHeader
import monero.daemon.model.MoneroBlockTemplate

/**
 * What the tests of the daemon check in the blocks, their headers and the templates to mine them, like BlockUtils of monero-python.
 */
object BlockAssertions {

    private val logger = TestLog.logger("BlockAssertions")

    /**
     * Check a block header: the fields that every header has, and the full ones if [isFull] says that they are there, or that they are not.
     */
    fun testBlockHeader(header: MoneroBlockHeader, isFull: Boolean?) {
        val height = assertNotNull(header.height)
        assertTrue(height >= 0)
        assertTrue(assertNotNull(header.majorVersion) > 0)
        assertTrue(assertNotNull(header.minorVersion) >= 0)
        val timestamp = assertNotNull(header.timestamp)
        if (height == 0L) assertEquals(0L, timestamp) else assertTrue(timestamp > 0)
        assertNotNull(header.prevHash)
        val nonce = assertNotNull(header.nonce)
        if (nonce == 0L) {
            // monero-project gives 0 for the nonce of some blocks
            logger.warning("header nonce is 0 at height $height")
        } else {
            assertTrue(nonce > 0)
        }

        // never seen defined
        assertNull(header.powHash)
        testFullHeader(header, isFull)
    }

    /**
     * Check the details of a full header, which are there for the header of a block that is asked by itself and not for the ones that come
     * in a binary block.
     */
    fun testFullHeader(header: MoneroBlockHeader, isFull: Boolean?) {
        // the number of transactions and the hash are always there
        assertTrue(assertNotNull(header.numTxs) >= 0)
        assertEquals(64, assertNotNull(header.hash).length)

        if (isFull == true) {
            assertTrue(assertNotNull(header.size) > 0)
            assertTrue(assertNotNull(header.depth) >= 0)
            assertTrue(assertNotNull(header.difficultyLow).signum() > 0)
            assertTrue(assertNotNull(header.difficultyHigh).signum() >= 0)
            assertTrue(assertNotNull(header.cumulativeDifficultyLow).signum() > 0)
            assertTrue(assertNotNull(header.cumulativeDifficultyHigh).signum() >= 0)
            assertEquals(64, assertNotNull(header.minerTxHash).length)
            assertNotNull(header.orphanStatus)
            assertNotNull(header.reward)
            assertTrue(assertNotNull(header.weight) > 0)
        } else {
            assertNull(header.size)
            assertNull(header.depth)
            assertNull(header.difficultyLow)
            assertNull(header.difficultyHigh)
            assertNull(header.cumulativeDifficultyLow)
            assertNull(header.cumulativeDifficultyHigh)
            assertNull(header.minerTxHash)
            assertNull(header.orphanStatus)
            assertNull(header.reward)
            assertNull(header.weight)
        }
    }

    /**
     * Check a block as [ctx] says it was asked for: with its hex or without it, with a full header or not, and with its transactions or
     * without them.
     */
    fun testBlock(block: MoneroBlock, ctx: TestContext) {
        // the required fields
        assertNotNull(block.minerTx, "Expected block miner tx")
        DaemonTxAssertions.testMinerTx(block.minerTx)
        testBlockHeader(block, ctx.headerIsFull)

        if (ctx.hasHex == true) {
            assertTrue(assertNotNull(block.hex).length > 1)
        } else {
            assertNull(block.hex)
        }

        if (ctx.hasTxs == true) {
            val txContext = assertNotNull(ctx.txContext)
            for (tx in block.txs.orEmpty()) {
                assertSame(block, tx.block)
                DaemonTxAssertions.testTx(tx, txContext)
            }

            // no transaction is twice in the block
            val numBlockTxs = block.txs.orEmpty().size
            val txHashes = block.txHashes.orEmpty()
            val numTxs = block.numTxs
            if (numTxs != null) {
                assertEquals(numTxs, numBlockTxs, "Expected $numTxs, got $numBlockTxs")
                assertEquals(numTxs, txHashes.size, "Expected $numTxs, got ${txHashes.size}")
                assertEquals(txHashes.size, txHashes.toSet().size, "Duplicate tx hashes found in block")
            } else {
                assertEquals(0, numBlockTxs)
                assertEquals(0, txHashes.size)
            }
        } else {
            assertNull(ctx.txContext)
            assertTrue(block.txs.orEmpty().isEmpty(), "No txs expected")
        }
    }

    /**
     * Get the blocks of a range of heights, in one request or in chunks, and check that they are the blocks of the range. A start or an end
     * that is null is the start of the chain or its end.
     */
    fun testGetBlocksRange(daemon: MoneroDaemon, startHeight: Long?, endHeight: Long?, chainHeight: Long, chunked: Boolean, blockCtx: BinaryBlockContext) {
        val realStartHeight = startHeight ?: 0L
        val realEndHeight = endHeight ?: (chainHeight - 1)
        val blocks = if (chunked) daemon.getBlocksByRangeChunked(startHeight, endHeight) else daemon.getBlocksByRange(startHeight, endHeight)

        val expectedNumBlocks = realEndHeight - realStartHeight + 1
        assertEquals(expectedNumBlocks, blocks.size.toLong(), "Expected $expectedNumBlocks block(s), got ${blocks.size}")

        // each block
        for ((i, block) in blocks.withIndex()) {
            assertEquals(realStartHeight + i, block.height)
            testBlock(block, blockCtx)
        }
    }

    /**
     * Check a template to mine a block.
     */
    fun testBlockTemplate(template: MoneroBlockTemplate) {
        assertNotNull(template.blockTemplateBlob)
        assertNotNull(template.blockHashingBlob)
        assertNotNull(template.difficultyLow)
        assertNotNull(template.difficultyHigh)
        assertNotNull(template.expectedReward)
        assertNotNull(template.height)
        assertNotNull(template.prevHash)
        assertNotNull(template.reservedOffset)
        assertTrue(assertNotNull(template.seedHeight) >= 0)
        assertTrue(assertNotNull(template.seedHash).isNotEmpty())
        // the next seed hash is null or there
    }

    /**
     * Check an alternative chain of the daemon.
     */
    fun testAltChain(altChain: MoneroAltChain) {
        assertTrue(altChain.blockHashes.orEmpty().isNotEmpty())
        GenAssertions.testUnsignedBigInteger(altChain.difficultyLow, true)
        GenAssertions.testUnsignedBigInteger(altChain.difficultyHigh)
        assertTrue(assertNotNull(altChain.height) > 0)
        assertTrue(assertNotNull(altChain.length) > 0)
        assertEquals(64, assertNotNull(altChain.mainChainParentBlockHash).length)
    }
}
