package monero.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import monero.common.MoneroError
import monero.common.MoneroRpcConnection
import monero.wallet.model.MoneroSubaddress
import monero.wallet.model.MoneroSyncResult
import monero.wallet.model.MoneroTransfer
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroTxSet
import monero.wallet.model.MoneroTxWallet
import monero.wallet.model.MoneroWalletListener
import org.junit.jupiter.api.Tag

/**
 * What MoneroWalletDefault does with the functions that it writes with the ones that they call: the connection to a daemon from its
 * fields, the sync with a listener, one subaddress, the txs of some hashes in the order that was asked for, the transfers of an
 * account, and the description of an unsigned or a multisig tx. The functions that it does not write are tested by
 * MoneroWalletInterfaceTest.
 */
@Tag("unit")
class MoneroWalletDefaultTest {

    // overrides the functions that the others call, and records what they get
    private class Recorder : MoneroWalletDefault() {
        var connection: MoneroRpcConnection? = null
        var isTrusted: Boolean? = null
        var startHeight: Long? = -1
        var syncListener: MoneroWalletListener? = null
        val syncResult = MoneroSyncResult()
        var subaddressesOf: Pair<Int, List<Int>?>? = null
        var subaddresses: List<MoneroSubaddress> = emptyList()
        var txQuery: MoneroTxQuery? = null
        var txs: List<MoneroTxWallet> = emptyList()
        var transferQuery: MoneroTransferQuery? = null
        var described: MoneroTxSet? = null
        var labelled: Triple<Int, Int, String?>? = null

        override fun setDaemonConnection(connection: MoneroRpcConnection?, isTrusted: Boolean?) {
            this.connection = connection
            this.isTrusted = isTrusted
        }

        override fun sync(startHeight: Long?, listener: MoneroWalletListener?): MoneroSyncResult {
            this.startHeight = startHeight
            syncListener = listener
            return syncResult
        }

        override fun getSubaddresses(accountIdx: Int, subaddressIndices: List<Int>?): List<MoneroSubaddress> {
            subaddressesOf = Pair(accountIdx, subaddressIndices)
            return subaddresses
        }

        override fun getTxs(query: MoneroTxQuery?): List<MoneroTxWallet> {
            txQuery = query
            return txs
        }

        override fun getTransfers(query: MoneroTransferQuery?): List<MoneroTransfer> {
            transferQuery = query
            return emptyList()
        }

        override fun setSubaddressLabel(accountIdx: Int, subaddressIdx: Int, label: String?) {
            labelled = Triple(accountIdx, subaddressIdx, label)
        }

        override fun describeTxSet(txSet: MoneroTxSet): MoneroTxSet {
            described = txSet
            return txSet
        }
    }

    private val wallet = Recorder()

    private fun tx(hash: String?) = MoneroTxWallet().also { it.hash = hash }

    @Test
    fun testTestSetDaemonConnectionOfAUriMakesTheConnection() {
        wallet.setDaemonConnection("http://127.0.0.1:18081", "user", "abc123", "127.0.0.1:9050", true)
        val connection = wallet.connection!!
        assertEquals("http://127.0.0.1:18081", connection.uri)
        assertEquals("user", connection.username)
        assertEquals("abc123", connection.password)
        assertEquals("127.0.0.1:9050", connection.proxyUri)
        assertEquals(true, wallet.isTrusted)
    }

    @Test
    fun testTestSetDaemonConnectionOfAUriOnlyLeavesTheRestUnset() {
        wallet.setDaemonConnection("http://127.0.0.1:18081")
        val connection = wallet.connection!!
        assertNull(connection.username)
        assertNull(connection.password)
        assertNull(connection.proxyUri)
        assertNull(wallet.isTrusted)
    }

    @Test
    fun testTestSyncWithAListenerStartsAtTheLastHeight() {
        val listener = MoneroWalletListener()
        assertSame(wallet.syncResult, wallet.sync(listener))
        assertNull(wallet.startHeight)
        assertSame(listener, wallet.syncListener)
    }

    @Test
    fun testTestGetSubaddressAsksForTheIndexAndReturnsTheOne() {
        val subaddress = MoneroSubaddress()
        wallet.subaddresses = listOf(subaddress)
        assertSame(subaddress, wallet.getSubaddress(1, 2))
        assertEquals(Pair(1, listOf(2)), wallet.subaddressesOf)
    }

    @Test
    fun testTestGetSubaddressOfAnIndexThatIsNotThereFails() {
        val error = assertFailsWith<MoneroError> { wallet.getSubaddress(1, 2) }
        assertEquals("The subaddress 1,2 is not found", error.message)
    }

    @Test
    fun testTestGetSubaddressWhenTheWalletReturnsMoreFails() {
        wallet.subaddresses = listOf(MoneroSubaddress(), MoneroSubaddress())
        assertFailsWith<MoneroError> { wallet.getSubaddress(1, 2) }
    }

    @Test
    fun testTestGetTxsOfHashesQueriesTheHashes() {
        wallet.getTxs(listOf("a", "b"))
        assertEquals(listOf("a", "b"), wallet.txQuery!!.hashes)
    }

    @Test
    fun testTestGetTxsOfHashesAreInTheOrderOfTheHashes() {
        // the wallet returns them in the order of the blocks
        val a = tx("a")
        val b = tx("b")
        val c = tx("c")
        wallet.txs = listOf(a, b, c)
        assertEquals(listOf(c, a, b), wallet.getTxs(listOf("c", "a", "b")))
    }

    @Test
    fun testTestGetTxsOfHashesPutsWhatWasNotAskedForLast() {
        val a = tx("a")
        val other = tx("other")
        val noHash = tx(null)
        wallet.txs = listOf(other, noHash, a)
        assertEquals(listOf(a, other, noHash), wallet.getTxs(listOf("a")))
    }

    @Test
    fun testTestGetTxsOfRepeatedHashesKeepsTheFirstPosition() {
        val a = tx("a")
        val b = tx("b")
        wallet.txs = listOf(a, b)
        assertEquals(listOf(b, a), wallet.getTxs(listOf("b", "a", "b")))
    }

    @Test
    fun testTestGetTxAsksForTheHashAndReturnsTheFirst() {
        val first = tx("hash")
        wallet.txs = listOf(first, tx("hash"))
        assertSame(first, wallet.getTx("hash"))
        assertEquals(listOf("hash"), wallet.txQuery!!.hashes)
    }

    @Test
    fun testTestGetTxOfAnUnknownHashIsNull() {
        assertNull(wallet.getTx("hash"))
    }

    @Test
    fun testTestGetTransfersOfAnAccountQueriesTheAccount() {
        wallet.getTransfers(1)
        assertEquals(1, wallet.transferQuery!!.accountIndex)
        assertNull(wallet.transferQuery!!.subaddressIndex)
    }

    @Test
    fun testTestGetTransfersOfASubaddressQueriesTheSubaddress() {
        wallet.getTransfers(1, 2)
        assertEquals(1, wallet.transferQuery!!.accountIndex)
        assertEquals(2, wallet.transferQuery!!.subaddressIndex)
    }

    @Test
    fun testTestDescribeUnsignedTxSetDescribesAnUnsignedTxSet() {
        wallet.describeUnsignedTxSet("unsigned")
        assertEquals("unsigned", wallet.described!!.unsignedTxHex)
        assertNull(wallet.described!!.multisigTxHex)
    }

    @Test
    fun testTestDescribeMultisigTxSetDescribesAMultisigTxSet() {
        wallet.describeMultisigTxSet("multisig")
        assertEquals("multisig", wallet.described!!.multisigTxHex)
        assertNull(wallet.described!!.unsignedTxHex)
    }

    @Test
    fun testTestSetAccountLabelSetsTheLabelOfTheFirstSubaddress() {
        wallet.setAccountLabel(2, "savings")
        assertEquals(Triple(2, 0, "savings"), wallet.labelled)
    }
}
