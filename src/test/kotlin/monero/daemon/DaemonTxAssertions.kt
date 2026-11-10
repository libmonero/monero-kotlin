package monero.daemon

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.daemon.model.MoneroTx

/**
 * What the tests of the daemon check in the transactions that it gives, in the blocks, in the pool and in the answers of the requests by
 * hash, like TxUtils and TxTester of monero-python. What a transaction of a wallet has is in [monero.wallet.TxAssertions].
 */
object DaemonTxAssertions {

    /**
     * Check a transaction of the daemon, as [context] says it was asked for: pruned or not, confirmed or in the pool, and from a binary
     * block or not.
     */
    fun testTx(tx: MoneroTx?, context: TestContext) {
        assertNotNull(tx, "No tx provided")
        TxTester(tx, context).run()
    }

    /**
     * Check the miner transaction of a block, which has no inputs and no fee, and is confirmed.
     */
    fun testMinerTx(minerTx: MoneroTx?) {
        assertNotNull(minerTx)
        assertNotNull(minerTx.isMinerTx)
        assertTrue(assertNotNull(minerTx.version) >= 0)
        assertTrue(assertNotNull(minerTx.extra).isNotEmpty())
        assertTrue(assertNotNull(minerTx.unlockTime) >= BigInteger.ZERO)
        assertEquals(true, minerTx.isConfirmed)
    }

    private class TxTester(private val tx: MoneroTx, private val ctx: TestContext) {

        init {
            assertNotNull(ctx.isPruned)
            assertNotNull(ctx.isConfirmed)
            assertNotNull(ctx.fromGetTxPool)
        }

        fun run() {
            testStandard()
            testConfirmed()
            testInTxPool()
            testFailed()
            testInputsAndOutputs()
            testPruning()
        }

        // what every transaction has
        private fun testStandard() {
            assertEquals(64, assertNotNull(tx.hash).length)
            if (tx.isRelayed == null) assertEquals(true, tx.inTxPool) else assertNotNull(tx.isRelayed)
            assertNotNull(tx.isConfirmed)
            assertNotNull(tx.inTxPool)
            assertNotNull(tx.isMinerTx)
            assertNotNull(tx.isDoubleSpendSeen)
            assertTrue(assertNotNull(tx.version) >= 0)
            assertTrue(assertNotNull(tx.unlockTime) >= BigInteger.ZERO)
            assertTrue(assertNotNull(tx.extra).isNotEmpty())
            GenAssertions.testUnsignedBigInteger(tx.fee, true)

            // the output indices
            if (tx.isMinerTx == true) {
                assertTrue(tx.outputIndices.orEmpty().isEmpty())
                assertEquals(BigInteger.ZERO, tx.fee)
                assertTrue(tx.inputs.orEmpty().isEmpty())
                assertTrue(tx.signatures.orEmpty().isEmpty())
            }
            if (tx.inTxPool == true || ctx.fromGetTxPool == true || ctx.hasOutputIndices == false) {
                assertTrue(tx.outputIndices.orEmpty().isEmpty())
            } else {
                assertTrue(tx.outputIndices.orEmpty().isNotEmpty())
            }
        }

        private fun testConfirmed() {
            if (ctx.isConfirmed == true) assertEquals(true, tx.isConfirmed)
            else if (ctx.isConfirmed == false) assertEquals(false, tx.isConfirmed)

            if (tx.isConfirmed == true) {
                val block = assertNotNull(tx.block)
                assertTrue(block.txs.orEmpty().any { it === tx })
                assertTrue(assertNotNull(block.height) > 0)
                assertTrue(assertNotNull(block.timestamp) > 0)
                assertEquals(true, tx.relay)
                assertEquals(true, tx.isRelayed)
                assertEquals(false, tx.isFailed)
                assertEquals(false, tx.inTxPool)
                assertEquals(false, tx.isDoubleSpendSeen)
                if (ctx.fromBinaryBlock == true) {
                    assertNull(tx.numConfirmations)
                } else {
                    assertTrue(assertNotNull(tx.numConfirmations) > 0)
                }
            } else {
                // the transactions that no block holds are in a block without a height, which monero-c gives them
                assertNull(tx.block?.height, "Expected block tx to have no height")
                assertEquals(0L, tx.numConfirmations)
            }
        }

        private fun testInTxPool() {
            if (tx.inTxPool == true) {
                assertEquals(false, tx.isConfirmed)
                assertEquals(false, tx.isDoubleSpendSeen)
                assertNull(tx.lastFailedHeight)
                assertNull(tx.lastFailedHash)
                assertTrue(assertNotNull(tx.receivedTimestamp) > 0)
                if (ctx.fromGetTxPool == true) {
                    assertTrue(assertNotNull(tx.size) > 0)
                    assertTrue(assertNotNull(tx.weight) > 0)
                    assertNotNull(tx.isKeptByBlock)
                    assertTrue(assertNotNull(tx.maxUsedBlockHeight) >= 0)
                    assertNotNull(tx.maxUsedBlockHash)
                }
                assertNull(tx.lastFailedHeight)
                assertNull(tx.lastFailedHash)
            } else {
                assertNull(tx.lastRelayedTimestamp)
            }
        }

        private fun testFailed() {
            if (tx.isFailed == true) {
                assertTrue(assertNotNull(tx.receivedTimestamp) > 0)
            } else {
                if (tx.isRelayed == null) {
                    assertNull(tx.relay)
                } else if (tx.isRelayed == true) {
                    assertEquals(false, tx.isDoubleSpendSeen)
                } else {
                    assertEquals(false, tx.isRelayed)
                    if (ctx.fromGetTxPool == true) {
                        assertEquals(false, tx.relay)
                        assertNotNull(tx.isDoubleSpendSeen)
                    }
                }
            }
            assertNull(tx.lastFailedHeight)
            assertNull(tx.lastFailedHash)

            // the time of its reception is for the transactions of the pool and the ones that failed
            if (tx.receivedTimestamp != null) assertTrue(tx.inTxPool == true || tx.isFailed == true)
        }

        private fun testInputsAndOutputs() {
            if (tx.isMinerTx != true) assertTrue(tx.inputs.orEmpty().isNotEmpty())
            for (input in tx.inputs.orEmpty()) {
                assertTrue(tx === input.tx)
                OutputAssertions.testInput(input)
            }

            assertTrue(tx.outputs.orEmpty().isNotEmpty())
            for (output in tx.outputs.orEmpty()) {
                assertTrue(tx === output.tx)
                OutputAssertions.testOutput(output, ctx)
            }
        }

        // a transaction that is not pruned is complete, and a pruned one has less
        private fun testPruning() {
            // a transaction can be pruned whatever was asked
            val isPruned = tx.prunedHex != null
            if (ctx.isPruned == true) assertTrue(isPruned)
            if (ctx.fromGetTxPool == true || ctx.fromBinaryBlock == true) {
                assertNull(tx.prunableHash)
            } else {
                assertNotNull(tx.prunableHash)
            }

            if (isPruned) {
                assertNull(tx.rctSigPrunable)
                assertNull(tx.size)
                assertNull(tx.lastRelayedTimestamp)
                assertNull(tx.receivedTimestamp)
                assertNotNull(tx.prunedHex)
            } else {
                testFullTx()
            }
        }

        private fun testFullTx() {
            assertTrue(assertNotNull(tx.version) >= 0)
            assertTrue(assertNotNull(tx.unlockTime) >= BigInteger.ZERO)
            assertTrue(assertNotNull(tx.extra).isNotEmpty())

            if (ctx.fromBinaryBlock == true) {
                assertNull(tx.fullHex)
                assertNull(tx.rctSigPrunable)
            } else {
                assertTrue(assertNotNull(tx.fullHex).isNotEmpty())
            }

            assertEquals(false, tx.isDoubleSpendSeen)
            if (tx.isConfirmed == true) {
                assertNull(tx.lastRelayedTimestamp)
                assertNull(tx.receivedTimestamp)
            } else {
                if (tx.isRelayed == true) {
                    assertTrue(assertNotNull(tx.lastRelayedTimestamp) > 0)
                } else {
                    assertNull(tx.lastRelayedTimestamp)
                }
                assertTrue(assertNotNull(tx.receivedTimestamp) > 0)
            }
        }
    }
}
