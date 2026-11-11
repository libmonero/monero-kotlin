package monero.daemon

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import monero.common.MoneroError
import monero.daemon.model.MoneroBlock
import monero.daemon.model.MoneroBlockHeader
import monero.daemon.model.MoneroKeyImage
import monero.daemon.model.MoneroOutput
import monero.daemon.model.MoneroTx
import org.junit.jupiter.api.Tag

/**
 * The merge of the daemon models. It mirrors the tests of the merge of monero-python (tests/test_monero_daemon_model.py), which are the
 * rules of monero-cpp: a field that is set on one side is taken, the same value on both sides is kept, two values that differ are an
 * error unless the field changes (a height only grows, a transaction becomes confirmed and does not go back), and a transaction is the
 * same as another if it has the same hash.
 *
 * What is different from monero-python: a transaction without isConfirmed can be merged, as there it has to be set.
 */
@Tag("unit")
class MoneroDaemonModelMergeTest {

    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)

    private fun tx(hash: String?, confirmed: Boolean? = null) = MoneroTx().also {
        it.hash = hash
        it.isConfirmed = confirmed
    }

    private fun input(tx: MoneroTx, keyImage: String, amount: Long? = null) = MoneroOutput().also {
        it.tx = tx
        it.keyImage = MoneroKeyImage().also { image -> image.hex = keyImage }
        it.amount = amount?.let(BigInteger::valueOf)
    }

    // ----------------------------------- block header -----------------------------------

    @Test
    fun testBlockHeaderMergeFillsWhatIsNotSetAndTakesTheHigherHeightAndTimestamp() {
        val a = MoneroBlockHeader()
        a.hash = hashA
        a.height = 100
        a.timestamp = 1700000000
        val b = a.copy()
        b.height = 200
        b.timestamp = 1800000000
        b.size = 2000

        a.merge(b)
        assertEquals(200L, a.height)
        assertEquals(1800000000L, a.timestamp)
        assertEquals(2000L, a.size)
        assertEquals(hashA, a.hash)
    }

    @Test
    fun testBlockHeaderMergeNeverLowersTheHeightOrTheTimestamp() {
        val a = MoneroBlockHeader()
        a.height = 200
        a.timestamp = 1800000000
        val b = MoneroBlockHeader()
        b.height = 100
        b.timestamp = 1700000000
        a.merge(b)
        assertEquals(200L, a.height)
        assertEquals(1800000000L, a.timestamp)
    }

    @Test
    fun testBlockHeaderMergeOfAFieldThatDiffersFails() {
        val a = MoneroBlockHeader()
        a.hash = hashA
        val b = MoneroBlockHeader()
        b.hash = hashB
        val error = assertFailsWith<MoneroError> { a.merge(b) }
        assertTrue("Cannot reconcile" in error.message!!, error.message)
        assertEquals(hashA, a.hash)
    }

    @Test
    fun testBlockHeaderMergeOfTheDifficultyAndTheReward() {
        val a = MoneroBlockHeader()
        val b = MoneroBlockHeader()
        b.difficulty = BigInteger.ONE.shiftLeft(100)
        b.cumulativeDifficulty = BigInteger.TEN
        b.reward = BigInteger.valueOf(600000000000)
        a.merge(b)
        assertEquals(BigInteger.ONE.shiftLeft(100), a.difficulty)
        assertEquals(BigInteger.TEN, a.cumulativeDifficulty)
        assertEquals(BigInteger.valueOf(600000000000), a.reward)
    }

    @Test
    fun testBlockHeaderMergeOfItselfDoesNothing() {
        val a = MoneroBlockHeader()
        a.hash = hashA
        a.merge(a)
        assertEquals(hashA, a.hash)
    }

    // ----------------------------------- block -----------------------------------

    @Test
    fun testBlockMergeFillsTheHexAndTheTxHashes() {
        val a = MoneroBlock()
        a.hash = hashA
        a.height = 100
        val b = a.copy()
        b.hex = "deadbeef"
        b.txHashes = listOf(hashA, hashB)
        a.merge(b)
        assertEquals("deadbeef", a.hex)
        assertEquals(listOf(hashA, hashB), a.txHashes)
    }

    @Test
    fun testBlockMergeOfTxHashesThatDifferFails() {
        val a = MoneroBlock()
        a.txHashes = listOf(hashA)
        val b = MoneroBlock()
        b.txHashes = listOf(hashB)
        assertFailsWith<MoneroError> { a.merge(b) }
    }

    // a header that is called on a block merges the block
    @Test
    fun testBlockHeaderMergeOfABlockMergesTheBlock() {
        val a = MoneroBlock()
        a.height = 100
        val b = MoneroBlock()
        b.height = 100
        b.hex = "deadbeef"
        val header: MoneroBlockHeader = a
        header.merge(b)
        assertEquals("deadbeef", a.hex)
    }

    @Test
    fun testBlockMergeOfAHeaderMergesTheHeader() {
        val block = MoneroBlock()
        val header = MoneroBlockHeader()
        header.height = 7
        block.merge(header)
        assertEquals(7L, block.height)
    }

    @Test
    fun testBlockMergeAdoptsTheMinerTxOfTheOther() {
        val a = MoneroBlock()
        a.height = 100
        val b = MoneroBlock()
        b.height = 100
        val minerTx = tx(hashA)
        minerTx.isMinerTx = true
        b.minerTx = minerTx

        a.merge(b)
        assertSame(minerTx, a.minerTx)
        assertSame(a, a.minerTx!!.block)
    }

    @Test
    fun testBlockMergeMergesDistinctMinerTxs() {
        val a = MoneroBlock()
        val minerA = tx(hashA, true)
        a.minerTx = minerA
        val b = MoneroBlock()
        val minerB = tx(hashA, true)
        minerB.numConfirmations = 5
        b.minerTx = minerB

        a.merge(b)
        assertSame(minerA, a.minerTx)
        assertEquals(5L, minerA.numConfirmations)
        assertSame(a, minerA.block)
    }

    @Test
    fun testBlockMergeMergesTheTxsThatHaveTheSameHashAndAddsTheOthers() {
        val a = MoneroBlock()
        val txA = tx(hashA, true)
        a.txs = listOf(txA)
        val b = MoneroBlock()
        val sameTx = tx(hashA, true)
        sameTx.numConfirmations = 3
        val newTx = tx(hashB, true)
        b.txs = listOf(sameTx, newTx)

        a.merge(b)
        assertEquals(listOf(txA, newTx), a.txs)
        assertEquals(3L, txA.numConfirmations)
        assertSame(a, newTx.block)
        assertSame(a, txA.block)
    }

    @Test
    fun testBlockMergeKeepsTheTxsWithoutAHashApart() {
        val a = MoneroBlock()
        val txA = tx(null)
        a.txs = listOf(txA)
        val b = MoneroBlock()
        val txB = tx(null)
        b.txs = listOf(txB)

        a.merge(b)
        assertEquals(listOf(txA, txB), a.txs)
    }

    @Test
    fun testBlockMergeOfTheSameTxKeepsOneOfIt() {
        val a = MoneroBlock()
        val shared = tx(null)
        a.txs = listOf(shared)
        val b = MoneroBlock()
        b.txs = listOf(shared)
        a.merge(b)
        assertEquals(listOf(shared), a.txs)
    }

    // ----------------------------------- tx -----------------------------------

    @Test
    fun testTxMergeFillsWhatIsNotSet() {
        val a = tx(hashA, true)
        a.fee = BigInteger.valueOf(7500000)
        val b = a.copy()
        b.numConfirmations = 5
        b.size = 1500
        a.merge(b)
        assertEquals(5L, a.numConfirmations)
        assertEquals(1500L, a.size)
        assertEquals(BigInteger.valueOf(7500000), a.fee)
    }

    @Test
    fun testTxMergeOfATxWithoutIsConfirmedWorks() {
        val a = tx(hashA)
        val b = tx(hashA)
        b.numConfirmations = 2
        a.merge(b)
        assertEquals(2L, a.numConfirmations)
    }

    @Test
    fun testTxMergeOfAFieldThatDiffersFails() {
        val a = tx(hashA)
        val b = tx(hashB)
        val error = assertFailsWith<MoneroError> { a.merge(b) }
        assertTrue("Cannot reconcile" in error.message!!, error.message)
        val c = tx(hashA)
        c.fee = BigInteger.ONE
        val d = tx(hashA)
        d.fee = BigInteger.TWO
        assertFailsWith<MoneroError> { c.merge(d) }
    }

    @Test
    fun testTxMergeIsConfirmedCanBecomeTrueAndDoesNotGoBack() {
        val a = tx(hashA, false)
        a.merge(tx(hashA, true))
        assertEquals(true, a.isConfirmed)

        val c = tx(hashA, true)
        c.merge(tx(hashA, false))
        assertEquals(true, c.isConfirmed)
    }

    @Test
    fun testTxMergeIsDoubleSpendSeenRelayAndIsRelayedAndIsFailedCanBecomeTrue() {
        val a = tx(hashA, true)
        a.isDoubleSpendSeen = false
        a.relay = false
        a.isRelayed = false
        a.isFailed = false
        val b = a.copy()
        b.isDoubleSpendSeen = true
        b.relay = true
        b.isRelayed = true
        b.isFailed = true
        a.merge(b)
        assertEquals(true, a.isDoubleSpendSeen)
        assertEquals(true, a.relay)
        assertEquals(true, a.isRelayed)
        assertEquals(true, a.isFailed)
    }

    @Test
    fun testTxMergeInTxPoolCanBecomeTrue() {
        val a = tx(hashA, false)
        a.inTxPool = false
        val b = a.copy()
        b.inTxPool = true
        a.merge(b)
        assertEquals(true, a.inTxPool)
    }

    @Test
    fun testTxMergeIsLockedCanBecomeFalse() {
        val a = tx(hashA, true)
        a.isLocked = false
        val b = a.copy()
        b.isLocked = true
        a.merge(b)
        assertEquals(false, a.isLocked)

        val c = tx(hashA, true)
        c.isLocked = true
        val d = c.copy()
        d.isLocked = false
        c.merge(d)
        assertEquals(false, c.isLocked)
    }

    @Test
    fun testTxMergeNumConfirmationsIsTheHigher() {
        val a = tx(hashA, true)
        a.numConfirmations = 10
        val b = tx(hashA, true)
        b.numConfirmations = 4
        a.merge(b)
        assertEquals(10L, a.numConfirmations)
        b.merge(a)
        assertEquals(10L, b.numConfirmations)
    }

    @Test
    fun testTxMergeWhenConfirmedIsNotInThePoolAndHasNoTimesOfThePool() {
        val a = tx(hashA, false)
        a.inTxPool = true
        a.receivedTimestamp = 100
        a.lastRelayedTimestamp = 200
        val b = tx(hashA, true)
        a.merge(b)
        assertEquals(true, a.isConfirmed)
        assertEquals(false, a.inTxPool)
        assertNull(a.receivedTimestamp)
        assertNull(a.lastRelayedTimestamp)
    }

    @Test
    fun testTxMergeInThePoolKeepsTheEarliestReceivedAndTheLatestRelayed() {
        val a = tx(hashA, false)
        a.receivedTimestamp = 100
        a.lastRelayedTimestamp = 200
        val b = tx(hashA, false)
        b.receivedTimestamp = 50
        b.lastRelayedTimestamp = 300
        a.merge(b)
        assertEquals(50L, a.receivedTimestamp)
        assertEquals(300L, a.lastRelayedTimestamp)
    }

    @Test
    fun testTxMergeExtraAndOutputIndices() {
        val a = tx(hashA, true)
        val b = a.copy()
        b.extra = listOf(1, 2, 3, 255)
        b.outputIndices = listOf(100, 101)
        a.merge(b)
        assertEquals(listOf(1, 2, 3, 255), a.extra)
        assertEquals(listOf(100L, 101L), a.outputIndices)
    }

    @Test
    fun testTxMergeOfExtraThatDiffersFails() {
        val a = tx(hashA)
        a.extra = listOf(1)
        val b = tx(hashA)
        b.extra = listOf(2)
        assertFailsWith<MoneroError> { a.merge(b) }
    }

    @Test
    fun testTxMergeInputsMergesTheSameKeyImageAndAddsTheOthers() {
        val a = tx(hashA, true)
        a.inputs = listOf(input(a, "1".repeat(64)))
        val b = tx(hashA, true)
        b.inputs = listOf(input(b, "1".repeat(64), 5000), input(b, "2".repeat(64)))

        a.merge(b)
        assertEquals(2, a.inputs!!.size)
        assertEquals("1".repeat(64), a.inputs!![0].keyImage!!.hex)
        assertEquals(BigInteger.valueOf(5000), a.inputs!![0].amount)
        assertEquals("2".repeat(64), a.inputs!![1].keyImage!!.hex)
        assertSame(a, a.inputs!![1].tx)
        assertSame(a, a.inputs!![0].tx)
    }

    @Test
    fun testTxMergeInputsWithoutAKeyImageAreAdded() {
        val a = tx(hashA, true)
        a.inputs = listOf(MoneroOutput())
        val b = tx(hashA, true)
        b.inputs = listOf(MoneroOutput())
        a.merge(b)
        assertEquals(2, a.inputs!!.size)
    }

    @Test
    fun testTxMergeOutputsMergesTheSameKeyImageOrStealthKeyAndAddsTheOthers() {
        val a = tx(hashA, true)
        val byKeyImage = input(a, "1".repeat(64))
        val byStealthKey = MoneroOutput().also { it.stealthPublicKey = "s".repeat(64) }
        a.outputs = listOf(byKeyImage, byStealthKey)

        val b = tx(hashA, true)
        val sameKeyImage = input(b, "1".repeat(64), 10)
        val sameStealthKey = MoneroOutput().also { it.stealthPublicKey = "s".repeat(64); it.index = 7 }
        val other = MoneroOutput().also { it.stealthPublicKey = "t".repeat(64) }
        b.outputs = listOf(sameKeyImage, sameStealthKey, other)

        a.merge(b)
        assertEquals(3, a.outputs!!.size)
        assertSame(byKeyImage, a.outputs!![0])
        assertEquals(BigInteger.TEN, byKeyImage.amount)
        assertEquals(7L, byStealthKey.index)
        assertSame(other, a.outputs!![2])
        assertSame(a, other.tx)
    }

    @Test
    fun testTxMergeOutputsAreTheOnesOfTheOtherWhenThisHasNone() {
        val a = tx(hashA, true)
        val b = tx(hashA, true)
        val output = MoneroOutput()
        b.outputs = listOf(output)
        a.merge(b)
        assertEquals(listOf(output), a.outputs)
        assertSame(a, output.tx)
    }

    @Test
    fun testTxMergeAdoptsTheBlockOfTheOtherAndTakesItsPlaceInIt() {
        val block = MoneroBlock()
        block.height = 200
        val other = tx(hashA, true)
        block.txs = listOf(other)

        val tx = tx(hashA, true)
        tx.merge(other)
        assertSame(block, tx.block)
        assertSame(tx, block.txs!![0])
    }

    @Test
    fun testTxMergeInDifferentBlocksMergesTheBlocks() {
        val blockA = MoneroBlock()
        blockA.height = 100
        val txA = tx(hashA, true)
        blockA.txs = listOf(txA)

        val blockB = MoneroBlock()
        blockB.height = 100
        blockB.hex = "deadbeef"
        val txB = tx(hashA, true)
        txB.numConfirmations = 9
        blockB.txs = listOf(txB)

        txA.merge(txB)
        assertEquals("deadbeef", blockA.hex)
        assertEquals(9L, txA.numConfirmations)
        assertEquals(listOf(txA), blockA.txs)
    }

    @Test
    fun testTxMergeOfATxInABlockWithOneThatIsNotKeepsTheBlock() {
        val block = MoneroBlock()
        val inBlock = tx(hashA, true)
        block.txs = listOf(inBlock)
        val pool = tx(hashA, true)
        pool.numConfirmations = 4
        inBlock.merge(pool)
        assertSame(block, inBlock.block)
        assertEquals(4L, inBlock.numConfirmations)
    }

    @Test
    fun testTxMergeOfItselfDoesNothing() {
        val a = tx(hashA, true)
        a.inputs = listOf(input(a, "1".repeat(64)))
        a.merge(a)
        assertEquals(1, a.inputs!!.size)
    }

    // ----------------------------------- key image -----------------------------------

    @Test
    fun testKeyImageMergeFillsTheSignature() {
        val a = MoneroKeyImage()
        a.hex = hashA
        val b = a.copy()
        b.signature = "b".repeat(128)
        a.merge(b)
        assertEquals("b".repeat(128), a.signature)
    }

    @Test
    fun testKeyImageMergeOfAHexThatDiffersFails() {
        val a = MoneroKeyImage()
        a.hex = hashA
        val b = MoneroKeyImage()
        b.hex = hashB
        assertFailsWith<MoneroError> { a.merge(b) }
    }

    // ----------------------------------- output -----------------------------------

    @Test
    fun testOutputMergeAdoptsTheKeyImageOfTheOther() {
        val a = MoneroOutput()
        a.amount = BigInteger.valueOf(1000000)
        a.index = 5
        val b = a.copy()
        b.keyImage = MoneroKeyImage().also { it.hex = hashA }
        a.merge(b)
        assertEquals(hashA, a.keyImage!!.hex)
    }

    @Test
    fun testOutputMergeMergesTheKeyImages() {
        val a = MoneroOutput()
        a.keyImage = MoneroKeyImage().also { it.hex = hashA }
        val b = MoneroOutput()
        b.keyImage = MoneroKeyImage().also { it.hex = hashA; it.signature = "s" }
        a.merge(b)
        assertEquals("s", a.keyImage!!.signature)
    }

    @Test
    fun testOutputMergeRingOutputIndicesStealthPublicKeyAndMask() {
        val a = MoneroOutput()
        a.amount = BigInteger.valueOf(1000000)
        a.index = 5
        val b = a.copy()
        b.ringOutputIndices = listOf(10, 20, 30)
        b.stealthPublicKey = hashA
        b.mask = hashB
        a.merge(b)
        assertEquals(listOf(10L, 20L, 30L), a.ringOutputIndices)
        assertEquals(hashA, a.stealthPublicKey)
        assertEquals(hashB, a.mask)
    }

    @Test
    fun testOutputMergeOfAnAmountThatDiffersFails() {
        val a = MoneroOutput()
        a.amount = BigInteger.ONE
        val b = MoneroOutput()
        b.amount = BigInteger.TWO
        assertFailsWith<MoneroError> { a.merge(b) }
    }

    @Test
    fun testOutputMergeInDifferentTxsMergesTheTxs() {
        val txA = tx(hashA, true)
        val txB = tx(hashA, true)
        txB.numConfirmations = 9

        val outA = MoneroOutput().also { it.stealthPublicKey = "s".repeat(64) }
        txA.outputs = listOf(outA)
        val outB = MoneroOutput().also { it.stealthPublicKey = "s".repeat(64) }
        txB.outputs = listOf(outB)

        outA.merge(outB)
        assertEquals(9L, txA.numConfirmations)
        assertEquals(1, txA.outputs!!.size)
    }
}
