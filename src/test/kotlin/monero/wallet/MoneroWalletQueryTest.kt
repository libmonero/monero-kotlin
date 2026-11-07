package monero.wallet

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import monero.common.MoneroError
import monero.daemon.model.MoneroBlock
import monero.daemon.model.MoneroKeyImage
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroIncomingTransfer
import monero.wallet.model.MoneroOutgoingTransfer
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroTransfer
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroTxWallet
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory

/**
 * The queries of a wallet: which transactions, transfers and outputs meet them. It mirrors the tests of meets_criteria of monero-python (tests/test_monero_wallet_model.py), which are the
 * rules of monero-cpp: every field of the query that is set has to be the same in what it is asked of.
 *
 * What is different from monero-python: a transfer and an output that are asked about a query of their transaction, and have none,
 * do not meet it, where there it is a null pointer; and a query in Kotlin cannot be asked about nothing.
 *
 * What is different from monero-java: a transaction that is not in a block does not meet a query of a minimum height, as in monero-cpp.
 */
@Tag("unit")
class MoneroWalletQueryTest {

    private val address = "48W9YHwPzRz9aPTeXCA6kmSpW6HsvmWx578jj3of2gT3JwZzwTf33amESBoNDkL6SVK34Q2HTKqgYbGyE1hBws3wCrcBDR2"
    private val miningAddress = "49cvU1JnXFAH7r1RbDLJw78aaDnhW4sCKRbLaTYa9eHvcz9PK1YXwod5npWZvMyQ8L4waVjUhuCp6btFyELkRpA4SWNKeQH"
    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)

    private fun tx(hash: String? = hashA) = MoneroTxWallet().also { it.hash = hash }

    private fun confirmedTx(height: Long): MoneroTxWallet {
        val tx = tx()
        val block = MoneroBlock()
        block.height = height
        block.txs = listOf(tx)
        return tx
    }

    private fun incoming(amount: Long? = 1) = MoneroIncomingTransfer().also { it.amount = amount?.let(BigInteger::valueOf) }

    private fun outgoing(amount: Long? = 1) = MoneroOutgoingTransfer().also { it.amount = amount?.let(BigInteger::valueOf) }

    private fun wallet(amount: Long?) = MoneroOutputWallet().also { it.amount = amount?.let(BigInteger::valueOf) }

    // ----------------------------------- tx query -----------------------------------

    @Test
    fun txQuery_WithNothingSet_MeetsEveryTx() {
        assertTrue(MoneroTxQuery().meetsCriteria(tx()))
    }

    // one field of the query at a time: the same value meets it, and another does not
    @TestFactory
    fun txQuery_FieldsOfTheTx_AreMatched(): List<DynamicTest> {
        class Field(val name: String, val set: (MoneroTxWallet, Boolean) -> Unit, val setQuery: (MoneroTxQuery, Boolean) -> Unit)
        val fields = listOf(
            Field("isConfirmed", { t, v -> t.isConfirmed = v }, { q, v -> q.isConfirmed = v }),
            Field("inTxPool", { t, v -> t.inTxPool = v }, { q, v -> q.inTxPool = v }),
            Field("relay", { t, v -> t.relay = v }, { q, v -> q.relay = v }),
            Field("isRelayed", { t, v -> t.isRelayed = v }, { q, v -> q.isRelayed = v }),
            Field("isFailed", { t, v -> t.isFailed = v }, { q, v -> q.isFailed = v }),
            Field("isMinerTx", { t, v -> t.isMinerTx = v }, { q, v -> q.isMinerTx = v }),
            Field("isLocked", { t, v -> t.isLocked = v }, { q, v -> q.isLocked = v }),
            Field("isIncoming", { t, v -> t.isIncoming = v }, { q, v -> q.isIncoming = v }),
            Field("isOutgoing", { t, v -> t.isOutgoing = v }, { q, v -> q.isOutgoing = v })
        )
        val tests = ArrayList<DynamicTest>()
        for (field in fields) {
            tests.add(dynamicTest(field.name) {
                val tx = tx()
                field.set(tx, true)
                val query = MoneroTxQuery()
                field.setQuery(query, true)
                assertTrue(query.meetsCriteria(tx))
                field.setQuery(query, false)
                assertFalse(query.meetsCriteria(tx))
            })
            tests.add(dynamicTest("${field.name}, a tx that leaves it unset") {
                val query = MoneroTxQuery()
                field.setQuery(query, true)
                assertFalse(query.meetsCriteria(tx()))
            })
        }
        return tests
    }

    @Test
    fun txQuery_HashAndPaymentId_AreMatched() {
        val tx = tx()
        tx.paymentId = "c".repeat(16)
        val query = MoneroTxQuery()
        query.hash = hashA
        query.paymentId = "c".repeat(16)
        assertTrue(query.meetsCriteria(tx))
        query.hash = hashB
        assertFalse(query.meetsCriteria(tx))
        query.hash = hashA
        query.paymentId = "d".repeat(16)
        assertFalse(query.meetsCriteria(tx))
    }

    @Test
    fun txQuery_HasPaymentId() {
        val withPaymentId = tx()
        withPaymentId.paymentId = "b".repeat(16)
        val withoutPaymentId = tx(hashB)

        val yes = MoneroTxQuery()
        yes.hasPaymentId = true
        assertTrue(yes.meetsCriteria(withPaymentId))
        assertFalse(yes.meetsCriteria(withoutPaymentId))

        val no = MoneroTxQuery()
        no.hasPaymentId = false
        assertTrue(no.meetsCriteria(withoutPaymentId))
        assertFalse(no.meetsCriteria(withPaymentId))
    }

    @Test
    fun txQuery_Hashes() {
        val query = MoneroTxQuery()
        query.hashes = listOf(hashA, hashB)
        assertTrue(query.meetsCriteria(tx()))
        query.hashes = listOf("c".repeat(64))
        assertFalse(query.meetsCriteria(tx()))
        // a tx without a hash is not one of the hashes
        assertFalse(query.meetsCriteria(tx(null)))
    }

    @Test
    fun txQuery_PaymentIds() {
        val withPaymentId = tx()
        withPaymentId.paymentId = "b".repeat(16)
        val query = MoneroTxQuery()
        query.paymentIds = listOf("b".repeat(16), "d".repeat(16))
        assertTrue(query.meetsCriteria(withPaymentId))
        assertFalse(query.meetsCriteria(tx(hashB)))
        query.paymentIds = listOf("d".repeat(16))
        assertFalse(query.meetsCriteria(withPaymentId))
    }

    @Test
    fun txQuery_Heights() {
        val tx = confirmedTx(100)

        val height = MoneroTxQuery()
        height.height = 100
        assertTrue(height.meetsCriteria(tx))
        height.height = 200
        assertFalse(height.meetsCriteria(tx))

        val min = MoneroTxQuery()
        min.minHeight = 50
        assertTrue(min.meetsCriteria(tx))
        min.minHeight = 100
        assertTrue(min.meetsCriteria(tx))
        min.minHeight = 150
        assertFalse(min.meetsCriteria(tx))

        val max = MoneroTxQuery()
        max.maxHeight = 150
        assertTrue(max.meetsCriteria(tx))
        max.maxHeight = 100
        assertTrue(max.meetsCriteria(tx))
        max.maxHeight = 50
        assertFalse(max.meetsCriteria(tx))
    }

    @Test
    fun txQuery_ByHeight_IsNeverMetByATxThatIsNotInABlock() {
        val unconfirmed = tx(hashB)
        for (query in listOf(MoneroTxQuery().also { it.height = 1 }, MoneroTxQuery().also { it.minHeight = 0 }, MoneroTxQuery().also { it.maxHeight = 100 })) {
            assertFalse(query.meetsCriteria(unconfirmed))
        }
    }

    @Test
    fun txQuery_QueryChildren_IsOnByDefault() {
        val query = MoneroTxQuery()
        query.inputQuery = MoneroOutputQuery()
        // the tx has no inputs
        assertFalse(query.meetsCriteria(tx()))
        assertFalse(query.meetsCriteria(tx(), true))
        assertTrue(query.meetsCriteria(tx(), false))
    }

    @Test
    fun txQuery_TransferQuery_NeedsATransferThatMeetsIt() {
        val query = MoneroTxQuery()
        val transferQuery = MoneroTransferQuery()
        transferQuery.amount = BigInteger.valueOf(500)
        query.transferQuery = transferQuery

        val withOutgoing = tx()
        withOutgoing.outgoingTransfer = outgoing(500)
        assertTrue(query.meetsCriteria(withOutgoing))

        val withIncoming = tx()
        withIncoming.incomingTransfers = listOf(incoming(500))
        assertTrue(query.meetsCriteria(withIncoming))

        val withOther = tx()
        withOther.outgoingTransfer = outgoing(1)
        assertFalse(query.meetsCriteria(withOther))
        assertFalse(query.meetsCriteria(tx()))

        // the outgoing transfer does not meet it and an incoming one does
        val both = tx()
        both.outgoingTransfer = outgoing(1)
        both.incomingTransfers = listOf(incoming(500))
        assertTrue(query.meetsCriteria(both))
    }

    @Test
    fun txQuery_InputQuery_NeedsAnInputThatMeetsIt() {
        val noInputs = tx()
        val withInput = tx(hashB)
        withInput.inputs = listOf(wallet(100))

        val query = MoneroTxQuery()
        val inputQuery = MoneroOutputQuery()
        query.inputQuery = inputQuery
        assertFalse(query.meetsCriteria(noInputs))
        assertTrue(query.meetsCriteria(withInput))

        inputQuery.amount = BigInteger.valueOf(999)
        assertFalse(query.meetsCriteria(withInput))
        inputQuery.amount = BigInteger.valueOf(100)
        assertTrue(query.meetsCriteria(withInput))
    }

    @Test
    fun txQuery_OutputQuery_NeedsAnOutputThatMeetsIt() {
        val noOutputs = tx()
        val withOutput = tx(hashB)
        withOutput.outputs = listOf(wallet(100))

        val query = MoneroTxQuery()
        val outputQuery = MoneroOutputQuery()
        query.outputQuery = outputQuery
        assertFalse(query.meetsCriteria(noOutputs))
        assertTrue(query.meetsCriteria(withOutput))

        outputQuery.amount = BigInteger.valueOf(999)
        assertFalse(query.meetsCriteria(withOutput))
        outputQuery.amount = BigInteger.valueOf(100)
        assertTrue(query.meetsCriteria(withOutput))
    }

    // ----------------------------------- decontextualize -----------------------------------

    @Test
    fun txQuery_Decontextualize_RemovesWhatDependsOnTheTxsAround() {
        val query = MoneroTxQuery()
        query.hash = hashA
        query.isIncoming = true
        query.isOutgoing = false
        query.transferQuery = MoneroTransferQuery()
        query.inputQuery = MoneroOutputQuery()
        query.outputQuery = MoneroOutputQuery()

        assertSame(query, query.decontextualize())
        assertNull(query.isIncoming)
        assertNull(query.isOutgoing)
        assertNull(query.transferQuery)
        assertNull(query.inputQuery)
        assertNull(query.outputQuery)
        assertEquals(hashA, query.hash)
    }

    // ----------------------------------- transfer query -----------------------------------

    @Test
    fun transferQuery_WithNothingSet_MeetsEveryTransfer() {
        assertTrue(MoneroTransferQuery().meetsCriteria(incoming(100), false))
        assertTrue(MoneroTransferQuery().meetsCriteria(outgoing(100), false))
    }

    @Test
    fun transferQuery_AmountAndAccount_AreMatched() {
        val transfer = incoming(1)
        transfer.amount = BigInteger.valueOf(100)
        transfer.accountIndex = 2
        val query = MoneroTransferQuery()
        query.amount = BigInteger.valueOf(100)
        query.accountIndex = 2
        assertTrue(query.meetsCriteria(transfer, false))
        query.amount = BigInteger.valueOf(999)
        assertFalse(query.meetsCriteria(transfer, false))
        query.amount = BigInteger.valueOf(100)
        query.accountIndex = 5
        assertFalse(query.meetsCriteria(transfer, false))
        // a transfer without the amount
        val noAmount = MoneroTransferQuery().also { it.amount = BigInteger.ONE }
        assertFalse(noAmount.meetsCriteria(incoming(null), false))
    }

    @Test
    fun transferQuery_IncomingOrOutgoing() {
        val incoming = incoming()
        val outgoing = outgoing()
        val incomingQuery = MoneroTransferQuery().also { it.isIncoming = true }
        assertTrue(incomingQuery.meetsCriteria(incoming, false))
        assertFalse(incomingQuery.meetsCriteria(outgoing, false))
        val outgoingQuery = MoneroTransferQuery().also { it.isIncoming = false }
        assertTrue(outgoingQuery.meetsCriteria(outgoing, false))
        assertFalse(outgoingQuery.meetsCriteria(incoming, false))
    }

    // has destinations is an idea of the outgoing transfers: it rejects an incoming one, whether it asks for them or not
    @Test
    fun transferQuery_HasDestinations_NeverMeetsAnIncomingTransfer() {
        for (value in listOf(true, false)) {
            assertFalse(MoneroTransferQuery().also { it.hasDestinations = value }.meetsCriteria(incoming(), false))
        }
    }

    @Test
    fun transferQuery_AddressFields_OfAnIncomingTransfer() {
        val transfer = incoming()
        transfer.address = address
        transfer.subaddressIndex = 3

        val byAddress = MoneroTransferQuery().also { it.address = address }
        assertTrue(byAddress.meetsCriteria(transfer, false))
        byAddress.address = miningAddress
        assertFalse(byAddress.meetsCriteria(transfer, false))

        val byAddresses = MoneroTransferQuery().also { it.addresses = listOf(address, miningAddress) }
        assertTrue(byAddresses.meetsCriteria(transfer, false))
        byAddresses.addresses = listOf(miningAddress)
        assertFalse(byAddresses.meetsCriteria(transfer, false))

        val byIndex = MoneroTransferQuery().also { it.subaddressIndex = 3 }
        assertTrue(byIndex.meetsCriteria(transfer, false))
        byIndex.subaddressIndex = 9
        assertFalse(byIndex.meetsCriteria(transfer, false))

        val byIndices = MoneroTransferQuery().also { it.subaddressIndices = listOf(3, 4) }
        assertTrue(byIndices.meetsCriteria(transfer, false))
        byIndices.subaddressIndices = listOf(9)
        assertFalse(byIndices.meetsCriteria(transfer, false))
    }

    @Test
    fun transferQuery_AddressFields_OfAnOutgoingTransfer() {
        val transfer = outgoing()
        transfer.addresses = listOf(address, miningAddress)

        // one address has to be one of the addresses of the transfer
        val byAddress = MoneroTransferQuery().also { it.address = address }
        assertTrue(byAddress.meetsCriteria(transfer, false))
        byAddress.address = "9" + "z".repeat(94)
        assertFalse(byAddress.meetsCriteria(transfer, false))
        assertFalse(byAddress.meetsCriteria(outgoing(), false))

        // a list of addresses has to have one of them
        val byAddresses = MoneroTransferQuery().also { it.addresses = listOf(miningAddress, "9" + "z".repeat(94)) }
        assertTrue(byAddresses.meetsCriteria(transfer, false))
        byAddresses.addresses = listOf("9" + "z".repeat(94))
        assertFalse(byAddresses.meetsCriteria(transfer, false))
    }

    @Test
    fun transferQuery_SubaddressFields_OfAnOutgoingTransfer() {
        val transfer = outgoing()
        transfer.subaddressIndices = listOf(3, 4)

        val byIndex = MoneroTransferQuery().also { it.subaddressIndex = 3 }
        assertTrue(byIndex.meetsCriteria(transfer, false))
        byIndex.subaddressIndex = 9
        assertFalse(byIndex.meetsCriteria(transfer, false))
        assertFalse(byIndex.meetsCriteria(outgoing(), false))

        val byIndices = MoneroTransferQuery().also { it.subaddressIndices = listOf(4, 100) }
        assertTrue(byIndices.meetsCriteria(transfer, false))
        byIndices.subaddressIndices = listOf(100, 200)
        assertFalse(byIndices.meetsCriteria(transfer, false))
    }

    @Test
    fun transferQuery_HasDestinations_OfAnOutgoingTransfer() {
        val with = outgoing()
        with.destinations = listOf(MoneroDestination(address, BigInteger.ONE))
        val without = outgoing()

        val yes = MoneroTransferQuery().also { it.hasDestinations = true }
        assertTrue(yes.meetsCriteria(with, false))
        assertFalse(yes.meetsCriteria(without, false))
        val no = MoneroTransferQuery().also { it.hasDestinations = false }
        assertTrue(no.meetsCriteria(without, false))
        assertFalse(no.meetsCriteria(with, false))
    }

    @Test
    fun transferQuery_OfATransferThatIsNeitherIncomingNorOutgoing_Fails() {
        val transfer = object : MoneroTransfer() {
            override val isIncoming: Boolean? = null
            override fun copy(): MoneroTransfer = this
        }
        val error = assertFailsWith<MoneroError> { MoneroTransferQuery().meetsCriteria(transfer, false) }
        assertEquals("Transfer must be MoneroIncomingTransfer or MoneroOutgoingTransfer", error.message)
    }

    @Test
    fun transferQuery_QueryParent_ChecksTheQueryOfTheTx() {
        val tx = tx()
        val transfer = incoming(100)
        transfer.tx = tx
        val query = MoneroTransferQuery()
        query.amount = BigInteger.valueOf(100)
        query.txQuery = MoneroTxQuery().also { it.hash = hashB }

        // queries the parent by default, and the query of the tx does not match
        assertFalse(query.meetsCriteria(transfer))
        assertTrue(query.meetsCriteria(transfer, false))
        query.txQuery!!.hash = hashA
        assertTrue(query.meetsCriteria(transfer))
    }

    @Test
    fun transferQuery_QueryParent_OfATransferWithoutATx_IsNotMet() {
        val query = MoneroTransferQuery()
        query.txQuery = MoneroTxQuery()
        assertFalse(query.meetsCriteria(incoming()))
        assertTrue(query.meetsCriteria(incoming(), false))
    }

    // ----------------------------------- output query -----------------------------------

    @Test
    fun outputQuery_WithNothingSet_MeetsEveryOutput() {
        assertTrue(MoneroOutputQuery().meetsCriteria(wallet(1), false))
    }

    @TestFactory
    fun outputQuery_FieldsOfTheOutput_AreMatched(): List<DynamicTest> {
        class Field(val name: String, val set: (MoneroOutputWallet) -> Unit, val setQuery: (MoneroOutputQuery, Boolean) -> Unit)
        val fields = listOf(
            Field("accountIndex", { it.accountIndex = 2 }, { q, same -> q.accountIndex = if (same) 2 else 9 }),
            Field("subaddressIndex", { it.subaddressIndex = 5 }, { q, same -> q.subaddressIndex = if (same) 5 else 1 }),
            Field("amount", { it.amount = BigInteger.valueOf(100) }, { q, same -> q.amount = BigInteger.valueOf(if (same) 100 else 1) }),
            Field("isSpent", { it.isSpent = true }, { q, same -> q.isSpent = same }),
            Field("isFrozen", { it.isFrozen = false }, { q, same -> q.isFrozen = !same })
        )
        val tests = ArrayList<DynamicTest>()
        for (field in fields) {
            tests.add(dynamicTest(field.name) {
                val output = wallet(1)
                field.set(output)
                val query = MoneroOutputQuery()
                field.setQuery(query, true)
                assertTrue(query.meetsCriteria(output, false))
                field.setQuery(query, false)
                assertFalse(query.meetsCriteria(output, false))
            })
            // a query never meets an output that leaves the field unset
            tests.add(dynamicTest("${field.name}, an output that leaves it unset") {
                val query = MoneroOutputQuery()
                field.setQuery(query, true)
                assertFalse(query.meetsCriteria(MoneroOutputWallet(), false))
            })
        }
        return tests
    }

    @Test
    fun outputQuery_KeyImage() {
        val output = wallet(1)
        output.keyImage = MoneroKeyImage().also {
            it.hex = hashA
            it.signature = "b".repeat(128)
        }

        val byHex = MoneroOutputQuery().also { it.keyImage = MoneroKeyImage().also { image -> image.hex = hashA } }
        assertTrue(byHex.meetsCriteria(output, false))
        byHex.keyImage!!.hex = "c".repeat(64)
        assertFalse(byHex.meetsCriteria(output, false))

        val bySignature = MoneroOutputQuery().also { it.keyImage = MoneroKeyImage().also { image -> image.signature = "b".repeat(128) } }
        assertTrue(bySignature.meetsCriteria(output, false))
        bySignature.keyImage!!.signature = "d".repeat(128)
        assertFalse(bySignature.meetsCriteria(output, false))

        // an output with no key image never meets a query by key image
        assertFalse(byHex.meetsCriteria(MoneroOutputWallet(), false))
        // and a query with a key image with nothing in it needs an output with a key image
        val empty = MoneroOutputQuery().also { it.keyImage = MoneroKeyImage() }
        assertTrue(empty.meetsCriteria(output, false))
        assertFalse(empty.meetsCriteria(MoneroOutputWallet(), false))
    }

    @Test
    fun outputQuery_SubaddressIndices() {
        val output = wallet(1)
        output.subaddressIndex = 7
        val query = MoneroOutputQuery().also { it.subaddressIndices = listOf(7, 8) }
        assertTrue(query.meetsCriteria(output, false))
        query.subaddressIndices = listOf(1, 2)
        assertFalse(query.meetsCriteria(output, false))
        assertFalse(query.meetsCriteria(MoneroOutputWallet(), false))
    }

    @Test
    fun outputQuery_MinAndMaxAmount() {
        val output = wallet(50)
        val min = MoneroOutputQuery().also { it.minAmount = BigInteger.TEN }
        assertTrue(min.meetsCriteria(output, false))
        min.minAmount = BigInteger.valueOf(100)
        assertFalse(min.meetsCriteria(output, false))
        val max = MoneroOutputQuery().also { it.maxAmount = BigInteger.valueOf(100) }
        assertTrue(max.meetsCriteria(output, false))
        max.maxAmount = BigInteger.TEN
        assertFalse(max.meetsCriteria(output, false))
        // an output without the amount is outside the limits
        assertFalse(min.meetsCriteria(MoneroOutputWallet(), false))
        assertFalse(max.meetsCriteria(MoneroOutputWallet(), false))
    }

    @Test
    fun outputQuery_QueryParent_ChecksTheQueryOfTheTx() {
        val tx = tx()
        val output = wallet(1)
        output.tx = tx
        val query = MoneroOutputQuery()
        query.amount = BigInteger.ONE
        query.txQuery = MoneroTxQuery().also { it.hash = hashB }
        assertFalse(query.meetsCriteria(output))
        assertTrue(query.meetsCriteria(output, false))
        query.txQuery!!.hash = hashA
        assertTrue(query.meetsCriteria(output))
        // an output that is in no tx cannot meet a query of its tx
        assertFalse(query.meetsCriteria(wallet(1)))
    }

    // ----------------------------------- copy -----------------------------------

    @Test
    fun transferQuery_Copy_HasItsOwnDestinations() {
        val query = MoneroTransferQuery()
        val destination = MoneroDestination(address, BigInteger.valueOf(100))
        query.destinations = listOf(destination)
        val copy = query.copy()
        assertTrue(copy.destinations!![0] !== destination)
        assertEquals(address, copy.destinations!![0].address)
        assertEquals(BigInteger.valueOf(100), copy.destinations!![0].amount)
    }

    @Test
    fun queries_CopyKeepWhatTheyMeet() {
        val tx = confirmedTx(100)
        tx.paymentId = "c".repeat(16)
        val txQuery = MoneroTxQuery()
        txQuery.hash = hashA
        txQuery.height = 100
        txQuery.isConfirmed = null
        assertTrue(txQuery.copy().meetsCriteria(tx))
        txQuery.height = 200
        assertFalse(txQuery.copy().meetsCriteria(tx))

        val outputQuery = MoneroOutputQuery().also {
            it.amount = BigInteger.valueOf(1000000)
            it.minAmount = BigInteger.valueOf(100000)
            it.maxAmount = BigInteger.valueOf(2000000)
        }
        assertTrue(outputQuery.copy().meetsCriteria(wallet(1000000), false))
        assertFalse(outputQuery.copy().meetsCriteria(wallet(1000001), false))
    }
}
