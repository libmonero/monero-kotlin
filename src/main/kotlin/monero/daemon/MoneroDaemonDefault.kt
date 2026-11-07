package monero.daemon

import java.math.BigInteger
import monero.common.MoneroError
import monero.daemon.model.MoneroAddAuxiliaryPowResult
import monero.daemon.model.MoneroAltChain
import monero.daemon.model.MoneroAuxiliaryPow
import monero.daemon.model.MoneroBan
import monero.daemon.model.MoneroBlock
import monero.daemon.model.MoneroBlockHeader
import monero.daemon.model.MoneroBlockTemplate
import monero.daemon.model.MoneroDaemonInfo
import monero.daemon.model.MoneroDaemonListener
import monero.daemon.model.MoneroDaemonNetworkStats
import monero.daemon.model.MoneroDaemonSyncInfo
import monero.daemon.model.MoneroDaemonUpdateCheckResult
import monero.daemon.model.MoneroDaemonUpdateDownloadResult
import monero.daemon.model.MoneroFeeEstimate
import monero.daemon.model.MoneroGenerateBlocksResult
import monero.daemon.model.MoneroGetBlockHashesResult
import monero.daemon.model.MoneroGetBlocksByHashResult
import monero.daemon.model.MoneroHardForkInfo
import monero.daemon.model.MoneroKeyImageSpentStatus
import monero.daemon.model.MoneroMinerData
import monero.daemon.model.MoneroMinerTxSum
import monero.daemon.model.MoneroMiningStatus
import monero.daemon.model.MoneroOutput
import monero.daemon.model.MoneroOutputDistributionEntry
import monero.daemon.model.MoneroOutputHistogramEntry
import monero.daemon.model.MoneroPeer
import monero.daemon.model.MoneroPruneResult
import monero.daemon.model.MoneroSubmitTxResult
import monero.daemon.model.MoneroTx
import monero.daemon.model.MoneroTxBacklogEntry
import monero.daemon.model.MoneroTxPoolStats
import monero.daemon.model.MoneroVersion

/**
 * Abstract default implementation of a Monero daemon: the functions that are the same for every one, written with the ones that
 * they call. What a daemon does not override throws a [MoneroError] that says that it is not supported, as the base class of
 * monero-python does, so a daemon implements what it can do and nothing else.
 */
abstract class MoneroDaemonDefault : MoneroDaemon {

    override fun getTx(txHash: String, prune: Boolean): MoneroTx? {
        return getTxs(listOf(txHash), prune).firstOrNull()
    }

    override fun getTxHex(txHash: String, prune: Boolean): String {
        return getTxHexes(listOf(txHash), prune)[0]
    }

    override fun relayTxByHash(txHash: String) {
        relayTxsByHash(listOf(txHash))
    }

    override fun flushTxPool(hash: String) {
        flushTxPool(listOf(hash))
    }

    override fun getKeyImageSpentStatus(keyImage: String): MoneroKeyImageSpentStatus {
        return getKeyImageSpentStatuses(listOf(keyImage))[0]
    }

    override fun setPeerBan(ban: MoneroBan) {
        setPeerBans(listOf(ban))
    }

    override fun submitBlock(blockBlob: String) {
        submitBlocks(listOf(blockBlob))
    }

    override fun removeBootstrapDaemon() {
        setBootstrapDaemon("")
    }

    // not supported until a daemon overrides them
    override fun addListener(listener: MoneroDaemonListener): Unit = throw notSupported("addListener")
    override fun removeListener(listener: MoneroDaemonListener): Unit = throw notSupported("removeListener")
    override fun getListeners(): List<MoneroDaemonListener> = throw notSupported("getListeners")
    override fun removeListeners(): Unit = throw notSupported("removeListeners")
    override fun getVersion(): MoneroVersion = throw notSupported("getVersion")
    override fun isTrusted(): Boolean = throw notSupported("isTrusted")
    override fun getHeight(): Long = throw notSupported("getHeight")
    override fun getBlockHash(height: Long): String = throw notSupported("getBlockHash")
    override fun getBlockTemplate(walletAddress: String, reserveSize: Int?): MoneroBlockTemplate = throw notSupported("getBlockTemplate")
    override fun getMinerData(): MoneroMinerData = throw notSupported("getMinerData")
    override fun calculatePow(majorVersion: Int, height: Long, blockBlob: String, seedHash: String): String = throw notSupported("calculatePow")
    override fun addAuxiliaryPow(blockTemplateBlob: String, auxPow: List<MoneroAuxiliaryPow>): MoneroAddAuxiliaryPowResult = throw notSupported("addAuxiliaryPow")
    override fun getLastBlockHeader(): MoneroBlockHeader = throw notSupported("getLastBlockHeader")
    override fun getBlockHeaderByHash(blockHash: String): MoneroBlockHeader = throw notSupported("getBlockHeaderByHash")
    override fun getBlockHeaderByHeight(height: Long): MoneroBlockHeader = throw notSupported("getBlockHeaderByHeight")
    override fun getBlockHeadersByRange(startHeight: Long, endHeight: Long): List<MoneroBlockHeader> = throw notSupported("getBlockHeadersByRange")
    override fun getBlockByHash(blockHash: String): MoneroBlock = throw notSupported("getBlockByHash")
    override fun getBlocksByHash(blockHashes: List<String>, startHeight: Long, prune: Boolean, maxBlockCount: Long): MoneroGetBlocksByHashResult = throw notSupported("getBlocksByHash")
    override fun getBlockByHeight(height: Long): MoneroBlock = throw notSupported("getBlockByHeight")
    override fun getBlocksByHeight(heights: List<Long>): List<MoneroBlock> = throw notSupported("getBlocksByHeight")
    override fun getBlocksByRange(startHeight: Long?, endHeight: Long?): List<MoneroBlock> = throw notSupported("getBlocksByRange")
    override fun getBlocksByRangeChunked(startHeight: Long?, endHeight: Long?, maxChunkSize: Long?): List<MoneroBlock> = throw notSupported("getBlocksByRangeChunked")
    override fun getBlockHashes(blockHashes: List<String>): MoneroGetBlockHashesResult = throw notSupported("getBlockHashes")
    override fun getTxs(txHashes: Collection<String>, prune: Boolean): List<MoneroTx> = throw notSupported("getTxs")
    override fun getTxHexes(txHashes: Collection<String>, prune: Boolean): List<String> = throw notSupported("getTxHexes")
    override fun getMinerTxSum(height: Long, numBlocks: Long): MoneroMinerTxSum = throw notSupported("getMinerTxSum")
    override fun getFeeEstimate(graceBlocks: Long): MoneroFeeEstimate = throw notSupported("getFeeEstimate")
    override fun submitTxHex(txHex: String, doNotRelay: Boolean): MoneroSubmitTxResult = throw notSupported("submitTxHex")
    override fun relayTxsByHash(txHashes: Collection<String>): Unit = throw notSupported("relayTxsByHash")
    override fun getTxPool(): List<MoneroTx> = throw notSupported("getTxPool")
    override fun getTxPoolHashes(): List<String> = throw notSupported("getTxPoolHashes")
    override fun getTxPoolBacklog(): List<MoneroTxBacklogEntry> = throw notSupported("getTxPoolBacklog")
    override fun getTxPoolStats(): MoneroTxPoolStats = throw notSupported("getTxPoolStats")
    override fun flushTxPool(): Unit = throw notSupported("flushTxPool")
    override fun flushTxPool(hashes: Collection<String>): Unit = throw notSupported("flushTxPool")
    override fun getKeyImageSpentStatuses(keyImages: List<String>): List<MoneroKeyImageSpentStatus> = throw notSupported("getKeyImageSpentStatuses")
    override fun getOutputIndices(txHash: String): List<Long> = throw notSupported("getOutputIndices")
    override fun getOutputs(outputs: Collection<MoneroOutput>): List<MoneroOutput> = throw notSupported("getOutputs")
    override fun getOutputHistogram(amounts: Collection<BigInteger>, minCount: Int?, maxCount: Int?, isUnlocked: Boolean?, recentCutoff: Int?): List<MoneroOutputHistogramEntry> = throw notSupported("getOutputHistogram")
    override fun getOutputDistribution(amounts: Collection<BigInteger>, isCumulative: Boolean?, startHeight: Long?, endHeight: Long?): List<MoneroOutputDistributionEntry> = throw notSupported("getOutputDistribution")
    override fun getInfo(): MoneroDaemonInfo = throw notSupported("getInfo")
    override fun getSyncInfo(): MoneroDaemonSyncInfo = throw notSupported("getSyncInfo")
    override fun getNetworkStats(): MoneroDaemonNetworkStats = throw notSupported("getNetworkStats")
    override fun getHardForkInfo(): MoneroHardForkInfo = throw notSupported("getHardForkInfo")
    override fun getAltChains(): List<MoneroAltChain> = throw notSupported("getAltChains")
    override fun getAltBlockHashes(): List<String> = throw notSupported("getAltBlockHashes")
    override fun getDownloadLimit(): Int = throw notSupported("getDownloadLimit")
    override fun setDownloadLimit(limit: Int): Int = throw notSupported("setDownloadLimit")
    override fun resetDownloadLimit(): Int = throw notSupported("resetDownloadLimit")
    override fun getUploadLimit(): Int = throw notSupported("getUploadLimit")
    override fun setUploadLimit(limit: Int): Int = throw notSupported("setUploadLimit")
    override fun resetUploadLimit(): Int = throw notSupported("resetUploadLimit")
    override fun getPeers(): List<MoneroPeer> = throw notSupported("getPeers")
    override fun getKnownPeers(): List<MoneroPeer> = throw notSupported("getKnownPeers")
    override fun getPublicPeers(includeOffline: Boolean): List<MoneroPeer> = throw notSupported("getPublicPeers")
    override fun setOutgoingPeerLimit(limit: Int): Unit = throw notSupported("setOutgoingPeerLimit")
    override fun setIncomingPeerLimit(limit: Int): Unit = throw notSupported("setIncomingPeerLimit")
    override fun getPeerBans(): List<MoneroBan> = throw notSupported("getPeerBans")
    override fun getPeerBan(address: String): MoneroBan = throw notSupported("getPeerBan")
    override fun setPeerBans(bans: List<MoneroBan>): Unit = throw notSupported("setPeerBans")
    override fun startMining(address: String, numThreads: Long?, isBackground: Boolean?, ignoreBattery: Boolean?): Unit = throw notSupported("startMining")
    override fun stopMining(): Unit = throw notSupported("stopMining")
    override fun getMiningStatus(): MoneroMiningStatus = throw notSupported("getMiningStatus")
    override fun submitBlocks(blockBlobs: Collection<String>): Unit = throw notSupported("submitBlocks")
    override fun pruneBlockchain(check: Boolean): MoneroPruneResult = throw notSupported("pruneBlockchain")
    override fun saveBlockchain(): Unit = throw notSupported("saveBlockchain")
    override fun popBlocks(numBlocks: Long): Long = throw notSupported("popBlocks")
    override fun flushCache(badBlocks: Boolean): Unit = throw notSupported("flushCache")
    override fun setBootstrapDaemon(address: String, username: String, password: String, proxy: String): Unit = throw notSupported("setBootstrapDaemon")
    override fun setLogHashRate(isVisible: Boolean): Unit = throw notSupported("setLogHashRate")
    override fun setLogLevel(level: Int): Unit = throw notSupported("setLogLevel")
    override fun setLogCategories(categories: String): String = throw notSupported("setLogCategories")
    override fun generateBlocks(address: String, numBlocks: Long, prevBlockHash: String?, startingNonce: Long?): MoneroGenerateBlocksResult = throw notSupported("generateBlocks")
    override fun checkForUpdate(): MoneroDaemonUpdateCheckResult = throw notSupported("checkForUpdate")
    override fun downloadUpdate(path: String): MoneroDaemonUpdateDownloadResult = throw notSupported("downloadUpdate")
    override fun stop(): Unit = throw notSupported("stop")
    override fun waitForNextBlockHeader(): MoneroBlockHeader = throw notSupported("waitForNextBlockHeader")

    private fun notSupported(method: String) = MoneroError("$method is not supported")
}
