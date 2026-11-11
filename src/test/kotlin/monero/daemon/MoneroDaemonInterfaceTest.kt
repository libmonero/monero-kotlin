package monero.daemon

import java.lang.reflect.Method
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertTrue
import monero.Covers
import monero.NotSupported
import monero.daemon.model.MoneroAuxiliaryPow
import monero.daemon.model.MoneroBan
import monero.daemon.model.MoneroDaemonListener
import monero.daemon.model.MoneroOutput
import org.junit.jupiter.api.Tag

/**
 * Mirrors the tests of the MoneroDaemon interface of monero-python (test_monero_daemon_interface.py), which call each function of the base class and
 * expect the error that says that it is not supported. MoneroDaemonDefault does that for the functions that an implementation does not
 * override, so these call an instance of it that overrides none. The functions that it implements with the ones that they call, e.g.
 * a function with a list of one element, call a function of the interface and so end in that error too: what they pass to it is the
 * test of MoneroDaemonDefaultTest.
 */
@Tag("unit")
class MoneroDaemonInterfaceTest {

    private val daemon: MoneroDaemon = object : MoneroDaemonDefault() {}

    // a function of the interface without a test here is a function whose default nobody has tried, and a test that calls none is a test that was
    // left behind. A test calls the function that its name says, or the one of its Covers when it has the name of the test of monero-python
    @Test
    fun testEveryFunctionOfTheInterfaceHasATest() {
        val tests = MoneroDaemonInterfaceTest::class.java.declaredMethods.filter { it.isAnnotationPresent(NotSupported::class.java) }
        val functions = MoneroDaemon::class.java.methods.map { it.name }.toSet()
        fun covered(test: Method): Set<String> = test.getAnnotation(Covers::class.java)?.let { setOf(it.function) } ?: functions.filter { function ->
            val prefix = "test" + function.replaceFirstChar { it.uppercase() }
            test.name == prefix || test.name.startsWith(prefix + "With")
        }.toSet()
        for (test in tests) assertTrue(covered(test).let { it.isNotEmpty() && functions.containsAll(it) }, "${test.name} calls no function of MoneroDaemon")
        val tested = tests.flatMap { covered(it) }.toSet()
        for (function in functions) assertTrue(function in tested, "MoneroDaemon.$function has no test")
    }

    @NotSupported
    @Test
    fun testAddListener() {
        daemon.addListener(MoneroDaemonListener())
    }

    @NotSupported
    @Test
    fun testRemoveListener() {
        daemon.removeListener(MoneroDaemonListener())
    }

    @NotSupported
    @Test
    fun testGetListeners() {
        daemon.getListeners()
    }

    @NotSupported
    @Test
    fun testRemoveListeners() {
        daemon.removeListeners()
    }

    @NotSupported
    @Test
    fun testGetVersion() {
        daemon.getVersion()
    }

    @NotSupported
    @Test
    fun testIsTrusted() {
        daemon.isTrusted()
    }

    @NotSupported
    @Test
    fun testGetHeight() {
        daemon.getHeight()
    }

    @NotSupported
    @Test
    fun testGetBlockHash() {
        daemon.getBlockHash(0L)
    }

    @NotSupported
    @Test
    fun testGetBlockTemplate() {
        daemon.getBlockTemplate("")
    }

    @NotSupported
    @Test
    fun testGetMinerData() {
        daemon.getMinerData()
    }

    @NotSupported
    @Test
    fun testCalculatePow() {
        daemon.calculatePow(0, 0L, "", "")
    }

    @NotSupported
    @Test
    fun testAddAuxiliaryPow() {
        daemon.addAuxiliaryPow("", emptyList<MoneroAuxiliaryPow>())
    }

    @NotSupported
    @Test
    fun testGetLastBlockHeader() {
        daemon.getLastBlockHeader()
    }

    @NotSupported
    @Test
    fun testGetBlockHeaderByHash() {
        daemon.getBlockHeaderByHash("")
    }

    @NotSupported
    @Test
    fun testGetBlockHeaderByHeight() {
        daemon.getBlockHeaderByHeight(0L)
    }

    @NotSupported
    @Test
    fun testGetBlockHeadersByRange() {
        daemon.getBlockHeadersByRange(0L, 0L)
    }

    @NotSupported
    @Test
    fun testGetBlockByHash() {
        daemon.getBlockByHash("")
    }

    @NotSupported
    @Test
    fun testGetBlocksByHash() {
        daemon.getBlocksByHash(emptyList<String>(), 0L)
    }

    @NotSupported
    @Test
    fun testGetBlockByHeight() {
        daemon.getBlockByHeight(0L)
    }

    @NotSupported
    @Test
    fun testGetBlocksByHeight() {
        daemon.getBlocksByHeight(emptyList<Long>())
    }

    @NotSupported
    @Test
    fun testGetBlocksByRange() {
        daemon.getBlocksByRange()
    }

    @NotSupported
    @Test
    fun testGetBlocksByRangeChunked() {
        daemon.getBlocksByRangeChunked()
    }

    @NotSupported
    @Test
    fun testGetBlockHashes() {
        daemon.getBlockHashes(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testGetTx() {
        daemon.getTx("")
    }

    @NotSupported
    @Test
    fun testGetTxs() {
        daemon.getTxs(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testGetTxHex() {
        daemon.getTxHex("")
    }

    @NotSupported
    @Test
    fun testGetTxHexes() {
        daemon.getTxHexes(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testGetMinerTxSum() {
        daemon.getMinerTxSum(0L, 0L)
    }

    @NotSupported
    @Test
    fun testGetFeeEstimate() {
        daemon.getFeeEstimate()
    }

    @NotSupported
    @Test
    fun testSubmitTxHex() {
        daemon.submitTxHex("")
    }

    @NotSupported
    @Test
    fun testRelayTxByHash() {
        daemon.relayTxByHash("")
    }

    @NotSupported
    @Test
    fun testRelayTxsByHash() {
        daemon.relayTxsByHash(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testGetTxPool() {
        daemon.getTxPool()
    }

    @NotSupported
    @Test
    fun testGetTxPoolHashes() {
        daemon.getTxPoolHashes()
    }

    @NotSupported
    @Test
    fun testGetTxPoolBacklog() {
        daemon.getTxPoolBacklog()
    }

    @NotSupported
    @Test
    fun testGetTxPoolStats() {
        daemon.getTxPoolStats()
    }

    @NotSupported
    @Test
    fun testFlushTxPoolWithoutArguments() {
        daemon.flushTxPool()
    }

    @NotSupported
    @Test
    fun testFlushTxPoolWithHash() {
        daemon.flushTxPool("")
    }

    @NotSupported
    @Test
    fun testFlushTxPoolWithHashes() {
        daemon.flushTxPool(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testGetKeyImageSpentStatus() {
        daemon.getKeyImageSpentStatus("")
    }

    @NotSupported
    @Test
    fun testGetKeyImageSpentStatuses() {
        daemon.getKeyImageSpentStatuses(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testGetOutputIndices() {
        daemon.getOutputIndices("")
    }

    @NotSupported
    @Test
    fun testGetOutputs() {
        daemon.getOutputs(emptyList<MoneroOutput>())
    }

    @NotSupported
    @Test
    fun testGetOutputHistogram() {
        daemon.getOutputHistogram()
    }

    @NotSupported
    @Test
    fun testGetOutputDistribution() {
        daemon.getOutputDistribution(emptyList<BigInteger>())
    }

    @NotSupported
    @Test
    fun testGetInfo() {
        daemon.getInfo()
    }

    @NotSupported
    @Test
    fun testGetSyncInfo() {
        daemon.getSyncInfo()
    }

    @NotSupported
    @Test
    fun testGetNetworkStats() {
        daemon.getNetworkStats()
    }

    @NotSupported
    @Test
    fun testGetHardForkInfo() {
        daemon.getHardForkInfo()
    }

    @NotSupported
    @Test
    fun testGetAltChains() {
        daemon.getAltChains()
    }

    @NotSupported
    @Test
    fun testGetAltBlockHashes() {
        daemon.getAltBlockHashes()
    }

    @NotSupported
    @Test
    fun testGetDownloadLimit() {
        daemon.getDownloadLimit()
    }

    @NotSupported
    @Test
    fun testSetDownloadLimit() {
        daemon.setDownloadLimit(0)
    }

    @NotSupported
    @Test
    fun testResetDownloadLimit() {
        daemon.resetDownloadLimit()
    }

    @NotSupported
    @Test
    fun testGetUploadLimit() {
        daemon.getUploadLimit()
    }

    @NotSupported
    @Test
    fun testSetUploadLimit() {
        daemon.setUploadLimit(0)
    }

    @NotSupported
    @Test
    fun testResetUploadLimit() {
        daemon.resetUploadLimit()
    }

    @NotSupported
    @Test
    fun testGetPeers() {
        daemon.getPeers()
    }

    @NotSupported
    @Test
    fun testGetKnownPeers() {
        daemon.getKnownPeers()
    }

    @NotSupported
    @Test
    fun testGetPublicPeers() {
        daemon.getPublicPeers()
    }

    @NotSupported
    @Test
    fun testSetOutgoingPeerLimit() {
        daemon.setOutgoingPeerLimit(0)
    }

    @NotSupported
    @Test
    fun testSetIncomingPeerLimit() {
        daemon.setIncomingPeerLimit(0)
    }

    @NotSupported
    @Test
    fun testGetPeerBans() {
        daemon.getPeerBans()
    }

    @NotSupported
    @Test
    fun testGetPeerBan() {
        daemon.getPeerBan("")
    }

    @NotSupported
    @Test
    fun testSetPeerBan() {
        daemon.setPeerBan(MoneroBan())
    }

    @NotSupported
    @Test
    fun testSetPeerBans() {
        daemon.setPeerBans(emptyList<MoneroBan>())
    }

    @NotSupported
    @Test
    fun testStartMining() {
        daemon.startMining("")
    }

    @NotSupported
    @Test
    fun testStopMining() {
        daemon.stopMining()
    }

    @NotSupported
    @Test
    fun testGetMiningStatus() {
        daemon.getMiningStatus()
    }

    @NotSupported
    @Test
    fun testSubmitBlock() {
        daemon.submitBlock("")
    }

    @NotSupported
    @Test
    fun testSubmitBlocks() {
        daemon.submitBlocks(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testPruneBlockchain() {
        daemon.pruneBlockchain()
    }

    @NotSupported
    @Test
    fun testSaveBlockchain() {
        daemon.saveBlockchain()
    }

    @NotSupported
    @Test
    fun testPopBlocks() {
        daemon.popBlocks(0L)
    }

    @NotSupported
    @Test
    fun testFlushCache() {
        daemon.flushCache()
    }

    @NotSupported
    @Test
    fun testSetBootstrapDaemon() {
        daemon.setBootstrapDaemon("")
    }

    @NotSupported
    @Test
    fun testRemoveBootstrapDaemon() {
        daemon.removeBootstrapDaemon()
    }

    @NotSupported
    @Test
    fun testSetLogHashRate() {
        daemon.setLogHashRate(false)
    }

    @NotSupported
    @Test
    fun testSetLogLevel() {
        daemon.setLogLevel(0)
    }

    @NotSupported
    @Test
    fun testSetLogCategories() {
        daemon.setLogCategories()
    }

    @NotSupported
    @Test
    fun testGenerateBlocks() {
        daemon.generateBlocks("", 0L)
    }

    @NotSupported
    @Test
    fun testCheckForUpdate() {
        daemon.checkForUpdate()
    }

    @NotSupported
    @Test
    fun testDownloadUpdate() {
        daemon.downloadUpdate()
    }

    @NotSupported
    @Test
    fun testDownloadUpdateWithPath() {
        daemon.downloadUpdate("")
    }

    @NotSupported
    @Test
    fun testStop() {
        daemon.stop()
    }

    @NotSupported
    @Test
    fun testWaitForNextBlockHeader() {
        daemon.waitForNextBlockHeader()
    }
}
