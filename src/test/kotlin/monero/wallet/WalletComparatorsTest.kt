package monero.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import monero.daemon.model.MoneroBlock
import monero.daemon.model.MoneroKeyImage
import monero.daemon.model.MoneroTx
import monero.wallet.model.IncomingTransferComparator
import monero.wallet.model.MoneroIncomingTransfer
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroTxWallet
import monero.wallet.model.OutputComparator
import org.junit.jupiter.api.Tag

/**
 * The order of the incoming transfers and of the outputs of a wallet, like incoming_transfer_lt_comparator and output_wallet_lt_comparator
 * of monero-python (tests/test_monero_wallet_model.py): by the height of the transaction, then by the account, the subaddress, and for
 * the outputs the index and the key image.
 */
@Tag("unit")
class WalletComparatorsTest {

    private fun transfer(tx: MoneroTxWallet?, account: Int?, subaddress: Int?) = MoneroIncomingTransfer().also {
        it.tx = tx
        it.accountIndex = account
        it.subaddressIndex = subaddress
    }

    private fun output(tx: MoneroTx?, account: Int?, subaddress: Int?, index: Long?, keyImage: String?) = MoneroOutputWallet().also {
        it.tx = tx
        it.accountIndex = account
        it.subaddressIndex = subaddress
        it.index = index
        it.keyImage = keyImage?.let { hex -> MoneroKeyImage().also { image -> image.hex = hex } }
    }

    private fun txAt(height: Long?): MoneroTxWallet {
        val tx = MoneroTxWallet()
        if (height != null) {
            val block = MoneroBlock()
            block.height = height
            block.txs = listOf(tx)
        }
        return tx
    }

    // ----------------------------------- incoming transfers -----------------------------------

    @Test
    fun testIncomingTransferComparatorOrdersBySubaddress() {
        val t1 = transfer(MoneroTxWallet(), 0, 0)
        val t2 = transfer(MoneroTxWallet(), 0, 1)
        val comparator = IncomingTransferComparator()
        assertTrue(comparator.compare(t1, t2) < 0)
        assertTrue(comparator.compare(t2, t1) > 0)
        assertEquals(listOf(t1, t2), listOf(t2, t1).sortedWith(comparator))
    }

    @Test
    fun testIncomingTransferComparatorOrdersByAccountBeforeSubaddress() {
        val low = transfer(null, 0, 5)
        val high = transfer(null, 1, 0)
        assertTrue(IncomingTransferComparator().compare(low, high) < 0)
    }

    @Test
    fun testIncomingTransferComparatorOrdersByHeightFirst() {
        val early = transfer(txAt(100), 5, 5)
        val late = transfer(txAt(200), 0, 0)
        val pool = transfer(txAt(null), 0, 0)
        val comparator = IncomingTransferComparator()
        assertTrue(comparator.compare(early, late) < 0)
        assertEquals(listOf(early, late, pool), listOf(pool, late, early).sortedWith(comparator))
    }

    @Test
    fun testIncomingTransferComparatorPutsATransferWithoutATxAfterTheOnesThatHaveOne() {
        val withTx = transfer(txAt(null), 9, 9)
        val withoutTx = transfer(null, 0, 0)
        assertTrue(IncomingTransferComparator().compare(withTx, withoutTx) < 0)
    }

    @Test
    fun testIncomingTransferComparatorOfTransfersWithoutIndicesPutsThemFirst() {
        val unset = transfer(null, null, null)
        val set = transfer(null, 0, 0)
        assertTrue(IncomingTransferComparator().compare(unset, set) < 0)
        assertEquals(0, IncomingTransferComparator().compare(unset, transfer(null, null, null)))
    }

    // ----------------------------------- outputs -----------------------------------

    @Test
    fun testOutputComparatorOrdersByIndex() {
        val o1 = output(MoneroTx(), 0, 0, 0, "a".repeat(64))
        val o2 = output(MoneroTx(), 0, 0, 1, "b".repeat(64))
        val comparator = OutputComparator()
        assertTrue(comparator.compare(o1, o2) < 0)
        assertTrue(comparator.compare(o2, o1) > 0)
    }

    @Test
    fun testOutputComparatorOrdersByAccount() {
        val tx = MoneroTx()
        val o1 = output(tx, 0, 0, 0, "a".repeat(64))
        val o2 = output(tx, 1, 0, 0, "a".repeat(64))
        assertTrue(OutputComparator().compare(o1, o2) < 0)
        assertTrue(OutputComparator().compare(o2, o1) > 0)
    }

    @Test
    fun testOutputComparatorOrdersBySubaddress() {
        val tx = MoneroTx()
        val o1 = output(tx, 0, 0, 0, "a".repeat(64))
        val o2 = output(tx, 0, 1, 0, "a".repeat(64))
        assertTrue(OutputComparator().compare(o1, o2) < 0)
        assertTrue(OutputComparator().compare(o2, o1) > 0)
    }

    @Test
    fun testOutputComparatorOrdersByKeyImageWhenTheRestIsTheSame() {
        val tx = MoneroTx()
        val o1 = output(tx, 0, 0, 0, "a".repeat(64))
        val o2 = output(tx, 0, 0, 0, "b".repeat(64))
        val comparator = OutputComparator()
        assertTrue(comparator.compare(o1, o2) < 0)
        assertTrue(comparator.compare(o2, o1) > 0)
        assertEquals(listOf(o1, o2), listOf(o2, o1).sortedWith(comparator))
        assertEquals(0, comparator.compare(o1, output(tx, 0, 0, 0, "a".repeat(64))))
    }

    @Test
    fun testOutputComparatorOrdersByHeightFirst() {
        val early = output(txAt(100), 9, 9, 9, "z".repeat(64))
        val late = output(txAt(200), 0, 0, 0, "a".repeat(64))
        assertTrue(OutputComparator().compare(early, late) < 0)
    }

    @Test
    fun testOutputComparatorPutsAnOutputWithoutAKeyImageFirst() {
        val tx = MoneroTx()
        val without = output(tx, 0, 0, 0, null)
        val with = output(tx, 0, 0, 0, "a".repeat(64))
        assertTrue(OutputComparator().compare(without, with) < 0)
    }
}
