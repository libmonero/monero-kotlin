package monero.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import monero.daemon.model.MoneroBlock
import monero.daemon.model.MoneroTx
import monero.daemon.model.TxHeightComparator
import org.junit.jupiter.api.Tag

/**
 * The order of the transactions by height, like tx_lt_height_comparator of monero-python (tests/test_monero_daemon_model.py): the
 * lowest first, the ones that are not in a block last, and the ones of a block in the order that they have in it.
 */
@Tag("unit")
class TxHeightComparatorTest {

    private fun txAtHeight(height: Long?): MoneroTx {
        val tx = MoneroTx()
        if (height != null) {
            val block = MoneroBlock()
            block.height = height
            block.txs = listOf(tx)
        }
        return tx
    }

    @Test
    fun testTxHeightComparatorOrdersByHeight() {
        val low = txAtHeight(100)
        val high = txAtHeight(200)
        val comparator = TxHeightComparator()
        assertTrue(comparator.compare(low, high) < 0)
        assertTrue(comparator.compare(high, low) > 0)
        assertEquals(listOf(low, high), listOf(high, low).sortedWith(comparator))
    }

    @Test
    fun testTxHeightComparatorPutsTheTxsThatAreNotInABlockLast() {
        val confirmed = txAtHeight(100)
        val unconfirmed = txAtHeight(null)
        val comparator = TxHeightComparator()
        assertTrue(comparator.compare(confirmed, unconfirmed) < 0)
        assertTrue(comparator.compare(unconfirmed, confirmed) > 0)
        assertEquals(0, comparator.compare(unconfirmed, txAtHeight(null)))
    }

    @Test
    fun testTxHeightComparatorKeepsTheOrderOfTheTxsOfABlock() {
        val block = MoneroBlock()
        block.height = 100
        val first = MoneroTx()
        val second = MoneroTx()
        block.txs = listOf(first, second)
        val comparator = TxHeightComparator()
        assertTrue(comparator.compare(first, second) < 0)
        assertTrue(comparator.compare(second, first) > 0)
        assertEquals(0, comparator.compare(first, first))
        assertEquals(listOf(first, second), listOf(second, first).sortedWith(comparator))
    }

    @Test
    fun testTxHeightComparatorSortsAMixOfBlocksAndThePool() {
        val pool = txAtHeight(null)
        val high = txAtHeight(300)
        val low = txAtHeight(100)
        assertEquals(listOf(low, high, pool), listOf(pool, high, low).sortedWith(TxHeightComparator()))
    }
}
