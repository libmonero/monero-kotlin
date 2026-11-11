package monero.wallet

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import monero.ModelTestSupport.assertSameJson
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.daemon.model.MoneroBlock
import monero.daemon.model.MoneroKeyImage
import monero.daemon.model.MoneroOutput
import monero.daemon.model.MoneroTx
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroIncomingTransfer
import monero.wallet.model.MoneroOutgoingTransfer
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroTxWallet
import org.junit.jupiter.api.Tag

/**
 * The merge of the wallet models, and the copy of the ones that have a merge. It mirrors the tests of monero-python
 * (tests/test_monero_wallet_model.py), which are the rules of monero-cpp: the ones of the daemon models, and for a transaction of a
 * wallet its transfers, which are merged when they are of the same account and subaddress.
 *
 * What is different from monero-python: a transaction without isConfirmed can be merged, as there it has to be set. The tests of
 * the functions that filter the transfers and the outputs by a query are with the queries.
 */
@Tag("unit")
class MoneroWalletModelMergeTest {

    private val address = "48W9YHwPzRz9aPTeXCA6kmSpW6HsvmWx578jj3of2gT3JwZzwTf33amESBoNDkL6SVK34Q2HTKqgYbGyE1hBws3wCrcBDR2"
    private val hash = "a".repeat(64)

    private fun tx(confirmed: Boolean? = true) = MoneroTxWallet().also {
        it.hash = hash
        it.isConfirmed = confirmed
    }

    private fun incoming(account: Int?, subaddress: Int?, amount: Long? = null) = MoneroIncomingTransfer().also {
        it.accountIndex = account
        it.subaddressIndex = subaddress
        it.amount = amount?.let(BigInteger::valueOf)
    }

    // ----------------------------------- incoming transfer -----------------------------------

    @Test
    fun testIncomingTransferCopyIsIndependent() {
        val transfer = incoming(0, 1, 500000)
        transfer.address = address
        transfer.numSuggestedConfirmations = 10
        val copy = transfer.copy()
        assertTrue(copy !== transfer)
        assertSameJson(MoneroJson.serialize(transfer), MoneroJson.serialize(copy))
        copy.accountIndex = 9
        assertEquals(0, transfer.accountIndex)
    }

    @Test
    fun testIncomingTransferMergeFillsWhatIsNotSet() {
        val a = incoming(0, 1, 500000)
        val b = a.copy()
        b.address = address
        a.merge(b)
        assertEquals(address, a.address)
    }

    @Test
    fun testIncomingTransferMergeOfAFieldThatDiffersFails() {
        val a = incoming(0, 1, 500000)
        assertFailsWith<MoneroError> { a.merge(incoming(1, 1, 500000)) }
        assertFailsWith<MoneroError> { a.merge(incoming(0, 2, 500000)) }
        assertFailsWith<MoneroError> { a.merge(incoming(0, 1, 600000)) }
        a.address = address
        val other = incoming(0, 1, 500000)
        other.address = "another"
        assertFailsWith<MoneroError> { a.merge(other) }
    }

    @Test
    fun testIncomingTransferMergeNumSuggestedConfirmationsIsTheLower() {
        val a = incoming(0, 1)
        a.numSuggestedConfirmations = 10
        val b = incoming(0, 1)
        b.numSuggestedConfirmations = 4
        a.merge(b)
        assertEquals(4L, a.numSuggestedConfirmations)
        val c = incoming(0, 1)
        c.merge(a)
        assertEquals(4L, c.numSuggestedConfirmations)
    }

    @Test
    fun testIncomingTransferMergeInDifferentTxsMergesTheTxs() {
        val txA = tx()
        val txB = tx()
        txB.numConfirmations = 9
        val a = incoming(0, null, 100)
        a.tx = txA
        val b = incoming(0, null, 100)
        b.tx = txB
        a.merge(b)
        assertEquals(9L, txA.numConfirmations)
    }

    // monero-project returns the transfer of a failed tx of the pool with an amount of zero, and it is not a conflict
    @Test
    fun testIncomingTransferMergeZeroAgainstAnAmountKeepsTheOriginal() {
        val tx = tx()
        val a = incoming(0, null, 500)
        a.tx = tx
        val b = incoming(0, null, 0)
        b.tx = tx
        a.merge(b)
        assertEquals(BigInteger.valueOf(500), a.amount)

        val c = incoming(0, null, 0)
        c.tx = tx
        c.merge(a)
        assertEquals(BigInteger.ZERO, c.amount)
    }

    // ----------------------------------- outgoing transfer -----------------------------------

    @Test
    fun testOutgoingTransferCopyIsIndependent() {
        val transfer = MoneroOutgoingTransfer()
        transfer.amount = BigInteger.valueOf(500000)
        transfer.accountIndex = 0
        transfer.addresses = listOf(address)
        transfer.subaddressIndices = listOf(0)
        transfer.destinations = listOf(MoneroDestination(address, BigInteger.valueOf(500000)))
        val copy = transfer.copy()
        assertTrue(copy !== transfer)
        assertSameJson(MoneroJson.serialize(transfer), MoneroJson.serialize(copy))
        assertTrue(copy.destinations!![0] !== transfer.destinations!![0])
    }

    @Test
    fun testOutgoingTransferMergeAdoptsWhatIsNotSet() {
        val a = MoneroOutgoingTransfer()
        a.amount = BigInteger.valueOf(500000)
        a.accountIndex = 0
        val b = a.copy()
        b.addresses = listOf(address)
        b.subaddressIndices = listOf(0)
        b.destinations = listOf(MoneroDestination(address, BigInteger.valueOf(500000)))
        a.merge(b)
        assertEquals(listOf(address), a.addresses)
        assertEquals(listOf(0), a.subaddressIndices)
        assertEquals(1, a.destinations!!.size)
    }

    @Test
    fun testOutgoingTransferMergeDestinationsWithUnsetFieldsAreTheSame() {
        val a = MoneroOutgoingTransfer()
        a.destinations = listOf(MoneroDestination())
        val b = MoneroOutgoingTransfer()
        b.destinations = listOf(MoneroDestination())
        a.merge(b)
        assertEquals(1, a.destinations!!.size)
    }

    @Test
    fun testOutgoingTransferMergeOfDestinationsThatDifferFails() {
        val a = MoneroOutgoingTransfer()
        a.destinations = listOf(MoneroDestination(address, BigInteger.ONE))
        val b = MoneroOutgoingTransfer()
        b.destinations = listOf(MoneroDestination(address, BigInteger.TWO))
        assertEquals("Destination vectors are different", assertFailsWith<MoneroError> { a.merge(b) }.message)

        val c = MoneroOutgoingTransfer()
        c.destinations = listOf(MoneroDestination(address, BigInteger.ONE), MoneroDestination(address, BigInteger.ONE))
        assertEquals("Destination vectors are different sizes", assertFailsWith<MoneroError> { a.merge(c) }.message)
    }

    @Test
    fun testOutgoingTransferMergeOfTheSameDestinationsKeepsThem() {
        val a = MoneroOutgoingTransfer()
        a.destinations = listOf(MoneroDestination(address, BigInteger.ONE))
        val b = a.copy()
        a.merge(b)
        assertEquals(listOf(address), a.destinations!!.map { it.address })
    }

    // ----------------------------------- output of a wallet -----------------------------------

    @Test
    fun testOutputWalletCopyIsIndependent() {
        val output = MoneroOutputWallet()
        output.amount = BigInteger.valueOf(1000000)
        output.index = 2
        output.accountIndex = 0
        output.subaddressIndex = 1
        output.isSpent = false
        output.isFrozen = false
        val copy = output.copy()
        assertTrue(copy !== output)
        assertSameJson(MoneroJson.serialize(output), MoneroJson.serialize(copy))
    }

    @Test
    fun testOutputWalletMergeFillsWhatIsNotSet() {
        val a = MoneroOutputWallet()
        a.amount = BigInteger.valueOf(1000000)
        a.index = 2
        a.accountIndex = 0
        a.subaddressIndex = 1
        val b = a.copy()
        b.isSpent = true
        b.isFrozen = false
        a.merge(b)
        assertEquals(true, a.isSpent)
        assertEquals(false, a.isFrozen)
    }

    @Test
    fun testOutputWalletMergeAlsoMergesTheOutput() {
        val a = MoneroOutputWallet()
        val b = MoneroOutputWallet()
        b.keyImage = MoneroKeyImage().also { it.hex = hash }
        b.stealthPublicKey = "s".repeat(64)
        a.merge(b)
        assertEquals(hash, a.keyImage!!.hex)
        assertEquals("s".repeat(64), a.stealthPublicKey)
    }

    @Test
    fun testOutputWalletMergeOfAFieldThatDiffersFails() {
        val a = MoneroOutputWallet()
        a.accountIndex = 0
        val b = MoneroOutputWallet()
        b.accountIndex = 1
        assertFailsWith<MoneroError> { a.merge(b) }
        val c = MoneroOutputWallet()
        c.isSpent = false
        val d = MoneroOutputWallet()
        d.isSpent = true
        assertFailsWith<MoneroError> { c.merge(d) }
    }

    @Test
    fun testOutputWalletMergeOfAnOutputThatIsNotOfAWalletMergesTheOutput() {
        val a = MoneroOutputWallet()
        val b = MoneroOutput()
        b.index = 4
        a.merge(b)
        assertEquals(4L, a.index)
    }

    // ----------------------------------- tx of a wallet -----------------------------------

    @Test
    fun testTxWalletCopyIsIndependent() {
        val tx = tx()
        tx.note = "hello"
        tx.incomingTransfers = listOf(incoming(0, 1, 5))
        val copy = tx.copy()
        assertTrue(copy !== tx)
        assertSameJson(MoneroJson.serialize(tx), MoneroJson.serialize(copy))
        assertTrue(copy.incomingTransfers!![0] !== tx.incomingTransfers!![0])
        assertSame(copy, copy.incomingTransfers!![0].tx)
    }

    @Test
    fun testTxWalletCopyReCopiesTheInputsAsOutputsOfAWallet() {
        val tx = MoneroTxWallet()
        tx.hash = hash
        val input = MoneroOutputWallet()
        input.tx = tx
        input.keyImage = MoneroKeyImage().also { it.hex = "1".repeat(64) }
        input.amount = BigInteger.valueOf(1234)
        input.accountIndex = 2
        input.subaddressIndex = 3
        input.isSpent = true
        input.isFrozen = false
        tx.inputs = listOf(input)

        val copy = tx.copy()
        val copied = copy.inputs!![0]
        assertEquals("MoneroOutputWallet", copied.javaClass.simpleName)
        assertTrue(copied !== input)
        copied as MoneroOutputWallet
        assertEquals(BigInteger.valueOf(1234), copied.amount)
        assertEquals(2, copied.accountIndex)
        assertEquals(3, copied.subaddressIndex)
        assertEquals(true, copied.isSpent)
        assertEquals(false, copied.isFrozen)
        assertEquals("1".repeat(64), copied.keyImage!!.hex)
        assertSame(copy, copied.tx)
    }

    @Test
    fun testTxWalletMergeFillsTheNote() {
        val a = tx()
        val b = a.copy()
        b.note = "hello"
        a.merge(b)
        assertEquals("hello", a.note)
    }

    @Test
    fun testTxWalletMergeOfTheFieldsOfTheWallet() {
        val a = tx()
        val b = tx()
        b.isIncoming = true
        b.isOutgoing = false
        b.inputSum = BigInteger.TEN
        b.outputSum = BigInteger.ONE
        b.changeAddress = address
        b.changeAmount = BigInteger.TWO
        b.numDummyOutputs = 3
        b.extraHex = "ab"
        a.merge(b)
        assertEquals(true, a.isIncoming)
        assertEquals(false, a.isOutgoing)
        assertEquals(BigInteger.TEN, a.inputSum)
        assertEquals(BigInteger.ONE, a.outputSum)
        assertEquals(address, a.changeAddress)
        assertEquals(BigInteger.TWO, a.changeAmount)
        assertEquals(3, a.numDummyOutputs)
        assertEquals("ab", a.extraHex)
    }

    @Test
    fun testTxWalletMergeOfAFieldThatDiffersFails() {
        val a = tx()
        a.note = "one"
        val b = tx()
        b.note = "two"
        assertFailsWith<MoneroError> { a.merge(b) }
    }

    @Test
    fun testTxWalletMergeIsLockedCanBecomeFalse() {
        val a = tx()
        a.isLocked = false
        val b = a.copy()
        b.isLocked = true
        a.merge(b)
        assertEquals(false, a.isLocked)
    }

    @Test
    fun testTxWalletMergeOfATxWithoutIsConfirmedWorks() {
        val a = tx(null)
        val b = tx(null)
        b.note = "hello"
        a.merge(b)
        assertEquals("hello", a.note)
    }

    @Test
    fun testTxWalletMergeOfATxThatIsNotOfAWalletMergesTheTx() {
        val a = tx()
        val b = MoneroTx()
        b.hash = hash
        b.numConfirmations = 6
        a.merge(b)
        assertEquals(6L, a.numConfirmations)
    }

    @Test
    fun testTxWalletMergeIncomingTransfersOfTheSameAccountAndSubaddressAreMerged() {
        val a = tx()
        val first = incoming(0, 1, 100)
        val second = incoming(0, 2, 200)
        a.incomingTransfers = listOf(first, second)

        val b = tx()
        val sameAsFirst = incoming(0, 1, 100)
        sameAsFirst.address = address
        val added = incoming(1, 0, 300)
        b.incomingTransfers = listOf(sameAsFirst, added)

        a.merge(b)
        assertEquals(listOf(first, second, added), a.incomingTransfers)
        assertEquals(address, first.address)
        assertSame(a, added.tx)
        assertSame(a, first.tx)
    }

    @Test
    fun testTxWalletMergeIncomingTransfersWithoutIndicesAreKeptDistinct() {
        val a = tx()
        val transferA = incoming(null, null)
        a.incomingTransfers = listOf(transferA)
        val b = tx()
        val transferB = incoming(null, null)
        b.incomingTransfers = listOf(transferB)
        a.merge(b)
        assertEquals(listOf(transferA, transferB), a.incomingTransfers)
    }

    @Test
    fun testTxWalletMergeIncomingTransfersWithOnlyTheAccountAreKeptDistinct() {
        val a = tx()
        a.incomingTransfers = listOf(incoming(0, null))
        val b = tx()
        b.incomingTransfers = listOf(incoming(0, null))
        a.merge(b)
        assertEquals(2, a.incomingTransfers!!.size)
    }

    @Test
    fun testTxWalletMergeOutgoingTransferIsTakenWhenThereIsNone() {
        val a = tx()
        val b = tx()
        val outgoing = MoneroOutgoingTransfer()
        outgoing.amount = BigInteger.valueOf(1000)
        b.outgoingTransfer = outgoing
        a.merge(b)
        assertSame(outgoing, a.outgoingTransfer)
        assertSame(a, outgoing.tx)
    }

    @Test
    fun testTxWalletMergeOutgoingTransfersAreMerged() {
        val a = tx()
        val mine = MoneroOutgoingTransfer()
        mine.amount = BigInteger.valueOf(1000)
        a.outgoingTransfer = mine
        val b = tx()
        val theirs = MoneroOutgoingTransfer()
        theirs.amount = BigInteger.valueOf(1000)
        theirs.addresses = listOf(address)
        b.outgoingTransfer = theirs
        a.merge(b)
        assertSame(mine, a.outgoingTransfer)
        assertEquals(listOf(address), mine.addresses)
    }

    @Test
    fun testTxWalletMergeInputsAreMergedAsOutputsOfAWallet() {
        val a = tx()
        val inputA = MoneroOutputWallet().also { it.keyImage = MoneroKeyImage().also { image -> image.hex = "1".repeat(64) } }
        a.inputs = listOf(inputA)
        val b = tx()
        val inputB = MoneroOutputWallet().also {
            it.keyImage = MoneroKeyImage().also { image -> image.hex = "1".repeat(64) }
            it.accountIndex = 2
            it.isSpent = true
        }
        b.inputs = listOf(inputB)
        a.merge(b)
        assertEquals(1, a.inputs!!.size)
        assertEquals(2, inputA.accountIndex)
        assertEquals(true, inputA.isSpent)
    }

    @Test
    fun testTxWalletMergeInDifferentBlocksMergesTheTransfersToo() {
        val blockA = MoneroBlock()
        blockA.height = 10
        val txA = tx()
        txA.incomingTransfers = listOf(incoming(0, 0, 5))
        blockA.txs = listOf(txA)
        val blockB = MoneroBlock()
        blockB.height = 10
        val txB = tx()
        val other = incoming(0, 1, 7)
        txB.incomingTransfers = listOf(other)
        blockB.txs = listOf(txB)

        txA.merge(txB)
        assertEquals(2, txA.incomingTransfers!!.size)
        assertEquals(listOf(txA), blockA.txs)
        assertNull(blockB.minerTx)
    }

    @Test
    fun testTxWalletMergeOfItselfDoesNothing() {
        val a = tx()
        a.incomingTransfers = listOf(incoming(0, 1))
        a.merge(a)
        assertEquals(1, a.incomingTransfers!!.size)
    }
}
