package monero.daemon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import monero.daemon.model.MoneroBan
import monero.daemon.model.MoneroKeyImageSpentStatus
import monero.daemon.model.MoneroTx
import org.junit.jupiter.api.Tag

/**
 * What MoneroDaemonDefault does with the functions that it writes with the ones that they call: the single hash, key image, ban
 * and block of the functions with a list, and the removal of the bootstrap daemon. The functions that it does not write are tested
 * by MoneroDaemonInterfaceTest.
 */
@Tag("unit")
class MoneroDaemonDefaultTest {

    // overrides the functions that the others call, and records what they get
    private class Recorder : MoneroDaemonDefault() {
        val calls = ArrayList<String>()
        var txs: List<MoneroTx> = emptyList()
        var hexes: List<String> = emptyList()
        var statuses: List<MoneroKeyImageSpentStatus> = emptyList()

        override fun getTxs(txHashes: Collection<String>, prune: Boolean): List<MoneroTx> {
            calls.add("getTxs $txHashes $prune")
            return txs
        }

        override fun getTxHexes(txHashes: Collection<String>, prune: Boolean): List<String> {
            calls.add("getTxHexes $txHashes $prune")
            return hexes
        }

        override fun relayTxsByHash(txHashes: Collection<String>) {
            calls.add("relayTxsByHash $txHashes")
        }

        override fun flushTxPool(hashes: Collection<String>) {
            calls.add("flushTxPool $hashes")
        }

        override fun getKeyImageSpentStatuses(keyImages: List<String>): List<MoneroKeyImageSpentStatus> {
            calls.add("getKeyImageSpentStatuses $keyImages")
            return statuses
        }

        override fun setPeerBans(bans: List<MoneroBan>) {
            calls.add("setPeerBans ${bans.map { it.host }}")
        }

        override fun submitBlocks(blockBlobs: Collection<String>) {
            calls.add("submitBlocks $blockBlobs")
        }

        override fun setBootstrapDaemon(address: String, username: String, password: String, proxy: String) {
            calls.add("setBootstrapDaemon '$address' '$username' '$password' '$proxy'")
        }
    }

    private val daemon = Recorder()

    @Test
    fun testGetTx_AsksForTheHashAndReturnsTheFirst() {
        val first = MoneroTx()
        daemon.txs = listOf(first, MoneroTx())
        assertSame(first, daemon.getTx("hash"))
        assertEquals(listOf("getTxs [hash] false"), daemon.calls)
    }

    @Test
    fun testGetTx_PassesPrune() {
        daemon.getTx("hash", true)
        assertEquals(listOf("getTxs [hash] true"), daemon.calls)
    }

    @Test
    fun testGetTx_OfAnUnknownHash_IsNull() {
        assertNull(daemon.getTx("hash"))
    }

    @Test
    fun testGetTxHex_AsksForTheHashAndReturnsTheFirst() {
        daemon.hexes = listOf("first", "second")
        assertEquals("first", daemon.getTxHex("hash", true))
        assertEquals(listOf("getTxHexes [hash] true"), daemon.calls)
    }

    @Test
    fun testRelayTxByHash_RelaysTheList() {
        daemon.relayTxByHash("hash")
        assertEquals(listOf("relayTxsByHash [hash]"), daemon.calls)
    }

    @Test
    fun testFlushTxPool_OfAHash_FlushesTheList() {
        daemon.flushTxPool("hash")
        assertEquals(listOf("flushTxPool [hash]"), daemon.calls)
    }

    @Test
    fun testGetKeyImageSpentStatus_AsksForTheKeyImageAndReturnsTheFirst() {
        daemon.statuses = listOf(MoneroKeyImageSpentStatus.entries[1], MoneroKeyImageSpentStatus.entries[0])
        assertEquals(MoneroKeyImageSpentStatus.entries[1], daemon.getKeyImageSpentStatus("image"))
        assertEquals(listOf("getKeyImageSpentStatuses [image]"), daemon.calls)
    }

    @Test
    fun testSetPeerBan_SetsTheList() {
        val ban = MoneroBan()
        ban.host = "127.0.0.1"
        daemon.setPeerBan(ban)
        assertEquals(listOf("setPeerBans [127.0.0.1]"), daemon.calls)
    }

    @Test
    fun testSubmitBlock_SubmitsTheList() {
        daemon.submitBlock("blob")
        assertEquals(listOf("submitBlocks [blob]"), daemon.calls)
    }

    @Test
    fun testRemoveBootstrapDaemon_SetsAnEmptyAddress() {
        daemon.removeBootstrapDaemon()
        assertEquals(listOf("setBootstrapDaemon '' '' '' ''"), daemon.calls)
    }
}
