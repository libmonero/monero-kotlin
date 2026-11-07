package monero.wallet

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import monero.wallet.model.MoneroIncomingTransfer
import monero.wallet.model.MoneroOutgoingTransfer
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroTxWallet
import org.junit.jupiter.api.Tag

/**
 * The inputs, the outputs and the transfers of a transaction of a wallet that meet a query, like get_inputs_wallet, filter_inputs_wallet,
 * get_outputs_wallet, filter_outputs_wallet, get_transfers and filter_transfers of monero-python (tests/test_monero_wallet_model.py): the
 * ones that get return the ones that meet the query, and the ones that filter remove the others from the transaction.
 */
@Tag("unit")
class MoneroTxWalletFilterTest {

    private val hashA = "a".repeat(64)

    private fun tx() = MoneroTxWallet().also { it.hash = hashA }

    private fun incoming(amount: Long) = MoneroIncomingTransfer().also { it.amount = BigInteger.valueOf(amount) }

    private fun outgoing(amount: Long) = MoneroOutgoingTransfer().also { it.amount = BigInteger.valueOf(amount) }


    private fun inputOf(account: Int, amount: Long) = MoneroOutputWallet().also {
        it.accountIndex = account
        it.amount = BigInteger.valueOf(amount)
    }

    @Test
    fun txWallet_FilterInputsWallet_KeepsWhatMeetsTheQuery() {
        val tx = tx()
        tx.inputs = listOf(inputOf(0, 100), inputOf(1, 200), inputOf(0, 300))
        val query = MoneroOutputQuery().also { it.accountIndex = 0 }

        val matched = tx.filterInputsWallet(query)
        assertEquals(listOf(BigInteger.valueOf(100), BigInteger.valueOf(300)), matched.map { it.amount })
        assertEquals(listOf(BigInteger.valueOf(100), BigInteger.valueOf(300)), tx.inputs!!.map { it.amount })
        assertEquals(2, tx.filterInputsWallet(MoneroOutputQuery()).size)
        assertTrue(tx.inputs!!.all { it.tx === tx })
    }

    @Test
    fun txWallet_GetInputsWallet_DoesNotChangeTheTx() {
        val tx = tx()
        tx.inputs = listOf(inputOf(0, 100), inputOf(1, 200), inputOf(0, 300))
        val matched = tx.getInputsWallet(MoneroOutputQuery().also { it.accountIndex = 0 })
        assertEquals(listOf(BigInteger.valueOf(100), BigInteger.valueOf(300)), matched.map { it.amount })
        assertEquals(3, tx.inputs!!.size)
        assertEquals(3, tx.getInputsWallet().size)
    }

    @Test
    fun txWallet_FilterOutputsWallet_KeepsWhatMeetsTheQuery() {
        val tx = tx()
        tx.outputs = listOf(inputOf(0, 100), inputOf(1, 200), inputOf(0, 300))
        val query = MoneroOutputQuery().also { it.accountIndex = 0 }
        val matched = tx.filterOutputsWallet(query)
        assertEquals(listOf(BigInteger.valueOf(100), BigInteger.valueOf(300)), matched.map { it.amount })
        assertEquals(listOf(BigInteger.valueOf(100), BigInteger.valueOf(300)), tx.outputs!!.map { it.amount })
        assertEquals(2, tx.filterOutputsWallet(MoneroOutputQuery()).size)
    }

    @Test
    fun txWallet_GetOutputsWallet_DoesNotChangeTheTx() {
        val tx = tx()
        tx.outputs = listOf(inputOf(0, 100), inputOf(1, 200))
        assertEquals(1, tx.getOutputsWallet(MoneroOutputQuery().also { it.accountIndex = 1 }).size)
        assertEquals(2, tx.outputs!!.size)
    }

    @Test
    fun txWallet_FilterTransfers_KeepsWhatMeetsTheQuery() {
        val tx = tx()
        tx.outgoingTransfer = outgoing(1000)
        tx.incomingTransfers = listOf(
            incoming(100).also { it.accountIndex = 0 },
            incoming(200).also { it.accountIndex = 1 }
        )
        val query = MoneroTransferQuery()
        query.isIncoming = true
        query.accountIndex = 0

        val matched = tx.filterTransfers(query)
        assertEquals(listOf(BigInteger.valueOf(100)), matched.map { it.amount })
        assertNull(tx.outgoingTransfer)
        assertEquals(listOf(BigInteger.valueOf(100)), tx.incomingTransfers!!.map { it.amount })
        assertSame(tx, tx.incomingTransfers!![0].tx)
    }

    @Test
    fun txWallet_GetTransfers_AreTheOutgoingOneThenTheIncomingOnes_AndDoNotChangeTheTx() {
        val tx = tx()
        tx.outgoingTransfer = outgoing(1000)
        tx.incomingTransfers = listOf(incoming(100), incoming(200))

        val all = tx.getTransfers()
        assertEquals(listOf(BigInteger.valueOf(1000), BigInteger.valueOf(100), BigInteger.valueOf(200)), all.map { it.amount })
        assertTrue(all[0] is MoneroOutgoingTransfer)
        assertTrue(all.drop(1).all { it is MoneroIncomingTransfer })

        val incomingOnly = tx.getTransfers(MoneroTransferQuery().also { it.isIncoming = true })
        assertEquals(2, incomingOnly.size)
        assertEquals(3, tx.getTransfers().size)
        assertTrue(tx.outgoingTransfer != null)
    }
}
