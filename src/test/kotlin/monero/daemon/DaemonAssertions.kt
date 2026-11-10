package monero.daemon

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.TestLog
import monero.daemon.model.MoneroBan
import monero.daemon.model.MoneroConnectionSpan
import monero.daemon.model.MoneroDaemonInfo
import monero.daemon.model.MoneroDaemonNetworkStats
import monero.daemon.model.MoneroDaemonSyncInfo
import monero.daemon.model.MoneroDaemonUpdateCheckResult
import monero.daemon.model.MoneroDaemonUpdateDownloadResult
import monero.daemon.model.MoneroHardForkInfo
import monero.daemon.model.MoneroKeyImageSpentStatus
import monero.daemon.model.MoneroMinerData
import monero.daemon.model.MoneroMinerTxSum
import monero.daemon.model.MoneroPeer
import monero.daemon.model.MoneroSubmitTxResult
import monero.daemon.model.MoneroTx
import monero.daemon.model.MoneroTxPoolStats

/**
 * What the tests of the daemon check in what it answers, like DaemonUtils of monero-python.
 */
object DaemonAssertions {

    private val logger = TestLog.logger("DaemonAssertions")

    /**
     * Check a peer that the daemon knows, which is one of its connections when [fromConnection] is true and one of its known peers when it
     * is not: only a known peer has the time that it was last seen.
     */
    fun testKnownPeer(peer: MoneroPeer, fromConnection: Boolean) {
        // what every peer has
        assertTrue(assertNotNull(peer.id).isNotEmpty())
        assertTrue(assertNotNull(peer.host).isNotEmpty())
        assertTrue(assertNotNull(peer.port) > 0)
        peer.rpcPort?.let { assertTrue(it >= 0) }
        assertNotNull(peer.isOnline)
        peer.pruningSeed?.let { assertTrue(it >= 0) }
        peer.rpcCreditsPerHash?.let { GenAssertions.testUnsignedBigInteger(it) }

        if (fromConnection) {
            assertNull(peer.lastSeenTimestamp)
        } else {
            assertTrue(assertNotNull(peer.lastSeenTimestamp) >= 0, "Last seen timestamp is invalid: ${peer.lastSeenTimestamp}")
        }
    }

    /**
     * Check a connection of the daemon with a peer.
     */
    fun testPeer(peer: MoneroPeer) {
        testKnownPeer(peer, true)
        assertTrue(assertNotNull(peer.hash).isNotEmpty())
        assertTrue(assertNotNull(peer.avgDownload) >= 0)
        assertTrue(assertNotNull(peer.avgUpload) >= 0)
        assertTrue(assertNotNull(peer.currentDownload) >= 0)
        assertTrue(assertNotNull(peer.currentUpload) >= 0)
        assertTrue(assertNotNull(peer.height) >= 0)
        assertTrue(assertNotNull(peer.liveTime) >= 0)
        assertNotNull(peer.isLocalIp)
        assertNotNull(peer.isLocalHost)
        assertTrue(assertNotNull(peer.numReceives) >= 0)
        assertTrue(assertNotNull(peer.receiveIdleTime) >= 0)
        assertTrue(assertNotNull(peer.numSends) >= 0)
        assertTrue(assertNotNull(peer.sendIdleTime) >= 0)
        assertNotNull(peer.state)
        assertTrue(assertNotNull(peer.numSupportFlags) >= 0)
        assertNotNull(peer.connectionType)
    }

    /**
     * Check the general information of the daemon.
     */
    fun testInfo(info: MoneroDaemonInfo) {
        assertNotNull(info.version)
        assertTrue(assertNotNull(info.numAltBlocks) >= 0)
        assertTrue(assertNotNull(info.blockSizeLimit) > 0)
        assertTrue(assertNotNull(info.blockSizeMedian) > 0)
        info.bootstrapDaemonAddress?.let { assertTrue(it.isNotEmpty()) }
        GenAssertions.testUnsignedBigInteger(info.difficultyLow)
        GenAssertions.testUnsignedBigInteger(info.difficultyHigh)
        GenAssertions.testUnsignedBigInteger(info.cumulativeDifficultyLow)
        GenAssertions.testUnsignedBigInteger(info.cumulativeDifficultyHigh)
        GenAssertions.testUnsignedBigInteger(info.freeSpace)
        assertTrue(assertNotNull(info.numOfflinePeers) >= 0)
        assertTrue(assertNotNull(info.numOnlinePeers) >= 0)
        assertTrue(assertNotNull(info.height) >= 0)
        assertTrue(assertNotNull(info.heightWithoutBootstrap) > 0)
        assertTrue(assertNotNull(info.numIncomingConnections) >= 0)
        assertTrue(assertNotNull(info.numOutgoingConnections) >= 0)
        assertNotNull(info.networkType)
        assertNotNull(info.isOffline)
        assertTrue(assertNotNull(info.numRpcConnections) >= 0)
        assertTrue(assertNotNull(info.startTimestamp) > 0)
        assertTrue(assertNotNull(info.adjustedTimestamp) > 0)
        assertTrue(assertNotNull(info.target) > 0)
        assertTrue(assertNotNull(info.targetHeight) >= 0)
        assertTrue(assertNotNull(info.numTxs) >= 0)
        assertTrue(assertNotNull(info.numTxsPool) >= 0)
        assertNotNull(info.wasBootstrapEverUsed)
        assertTrue(assertNotNull(info.blockWeightLimit) > 0)
        assertTrue(assertNotNull(info.blockWeightMedian) > 0)
        assertTrue(assertNotNull(info.databaseSize) > 0)
        assertNotNull(info.updateAvailable)

        // 0 credits
        GenAssertions.testUnsignedBigInteger(info.credits, false)
        assertTrue(assertNotNull(info.topBlockHash).isNotEmpty())
        assertNotNull(info.isBusySyncing)
        assertNotNull(info.isSynchronized)
        assertNotNull(info.isRegtest)
    }

    /**
     * Check a span of blocks that the daemon is downloading from a peer.
     */
    fun testConnectionSpan(span: MoneroConnectionSpan) {
        assertTrue(assertNotNull(span.connectionId).isNotEmpty())
        assertTrue(assertNotNull(span.numBlocks) >= 0)
        assertTrue(assertNotNull(span.remoteAddress).isNotEmpty())
        assertTrue(assertNotNull(span.rate) >= 0)
        assertTrue(assertNotNull(span.speed) >= 0)
        assertTrue(assertNotNull(span.size) >= 0)
        assertTrue(assertNotNull(span.startHeight) >= 0)
    }

    /**
     * Check the information of the daemon about its synchronization: its peers, the spans that it downloads and the next pruning seed that
     * it needs.
     */
    fun testSyncInfo(syncInfo: MoneroDaemonSyncInfo) {
        assertTrue(assertNotNull(syncInfo.height) >= 0)

        // the peers
        val peers = syncInfo.peers.orEmpty()
        assertTrue(peers.isNotEmpty(), "No peers found in daemon sync info to test.")
        for (connection in peers) testPeer(connection)

        // the spans
        for (span in syncInfo.spans.orEmpty()) testConnectionSpan(span)

        assertTrue(assertNotNull(syncInfo.nextNeededPruningSeed) >= 0)
        assertNull(syncInfo.overview)

        // 0 credits
        GenAssertions.testUnsignedBigInteger(syncInfo.credits, false)
        assertNull(syncInfo.topBlockHash)
    }

    /**
     * Check the information of the daemon about its hard forks.
     */
    fun testHardForkInfo(hardForkInfo: MoneroHardForkInfo) {
        assertNotNull(hardForkInfo.earliestHeight)
        assertNotNull(hardForkInfo.isEnabled)
        assertNotNull(hardForkInfo.state)
        assertNotNull(hardForkInfo.threshold)
        assertNotNull(hardForkInfo.version)
        assertNotNull(hardForkInfo.numVotes)
        assertNotNull(hardForkInfo.voting)
        assertNotNull(hardForkInfo.window)

        // 0 credits
        GenAssertions.testUnsignedBigInteger(hardForkInfo.credits, false)
        assertNull(hardForkInfo.topBlockHash)
    }

    /**
     * Check a ban of a peer.
     */
    fun testBan(ban: MoneroBan) {
        assertNotNull(ban.host)
        assertNotNull(ban.ip)
        assertNotNull(ban.seconds)
    }

    /**
     * Check the data that the daemon gives to mine a block.
     */
    fun testMinerData(data: MoneroMinerData) {
        assertTrue(assertNotNull(data.majorVersion) > 0)
        assertTrue(assertNotNull(data.height) > 0)
        assertEquals(64, assertNotNull(data.prevHash).length)
        assertEquals(64, assertNotNull(data.seedHash).length)

        // the difficulty is a hex string, such as "0x1f4"
        val difficulty = assertNotNull(data.difficulty)
        assertTrue(BigInteger(difficulty.removePrefix("0x"), 16).signum() > 0)
        assertTrue(assertNotNull(data.medianWeight) >= 0)
        assertTrue(assertNotNull(data.alreadyGeneratedCoins).signum() > 0)
        for (tx in data.txPoolBacklog.orEmpty()) assertEquals(64, assertNotNull(tx.hash).length)
    }

    /**
     * Check the statistics of the network of the daemon.
     */
    fun testNetworkStats(stats: MoneroDaemonNetworkStats) {
        assertTrue(assertNotNull(stats.startTime) > 0)
        assertTrue(assertNotNull(stats.totalPacketsIn) >= 0)
        assertTrue(assertNotNull(stats.totalBytesIn) >= 0)
        assertTrue(assertNotNull(stats.totalPacketsOut) >= 0)
        assertTrue(assertNotNull(stats.totalBytesOut) >= 0)
    }

    /**
     * Check the sum of the emission and of the fees of the miner transactions of a range of blocks.
     */
    fun testMinerTxSum(txSum: MoneroMinerTxSum) {
        GenAssertions.testUnsignedBigInteger(txSum.emissionSumLow)
        GenAssertions.testUnsignedBigInteger(txSum.emissionSumHigh)
        GenAssertions.testUnsignedBigInteger(txSum.feeSumLow)
        GenAssertions.testUnsignedBigInteger(txSum.feeSumHigh)
    }

    /**
     * Check the statistics of the transaction pool of the daemon: the sizes, the times and the counts that a pool with transactions has,
     * and the zeros and the nulls of an empty pool.
     */
    fun testTxPoolStats(stats: MoneroTxPoolStats) {
        val numTxs = assertNotNull(stats.numTxs)
        assertTrue(numTxs >= 0)
        if (numTxs > 0) {
            assertTrue(assertNotNull(stats.bytesMax) > 0)
            assertTrue(assertNotNull(stats.bytesMed) > 0)
            assertTrue(assertNotNull(stats.bytesMin) > 0)
            assertTrue(assertNotNull(stats.bytesTotal) > 0)
            assertTrue(assertNotNull(stats.oldestTimestamp) > 0)
            stats.histo98pc?.let { assertTrue(it > 0) }
            assertTrue(assertNotNull(stats.num10m) >= 0)
            assertTrue(assertNotNull(stats.numDoubleSpends) >= 0)
            assertTrue(assertNotNull(stats.numFailing) >= 0)
            assertTrue(assertNotNull(stats.numNotRelayed) >= 0)
        } else {
            assertNull(stats.bytesMax)
            assertNull(stats.bytesMed)
            assertNull(stats.bytesMin)
            assertEquals(0L, stats.bytesTotal)
            assertNull(stats.histo98pc)
            assertNull(stats.oldestTimestamp)
            assertEquals(0, stats.num10m)
            assertEquals(0, stats.numDoubleSpends)
            assertEquals(0, stats.numFailing)
            assertEquals(0, stats.numNotRelayed)
            assertTrue(stats.histo.orEmpty().isEmpty())
        }
    }

    /**
     * Check the result of the check for an update: an update that is available has its URIs, its version and its hash, and one that is not
     * has none of them.
     */
    fun testUpdateCheckResult(result: MoneroDaemonUpdateCheckResult) {
        assertNotNull(result.isUpdateAvailable)
        if (result.isUpdateAvailable == true) {
            val message = "No auto uri is daemon online?"
            assertTrue(assertNotNull(result.autoUri, message).isNotEmpty(), message)
            assertTrue(assertNotNull(result.userUri).isNotEmpty())
            assertTrue(assertNotNull(result.version).isNotEmpty())
            assertEquals(64, assertNotNull(result.hash).length)
        } else {
            assertNull(result.autoUri)
            assertNull(result.userUri)
            assertNull(result.version)
            assertNull(result.hash)
        }
    }

    /**
     * Check the result of the download of an update.
     */
    fun testUpdateDownloadResult(result: MoneroDaemonUpdateDownloadResult) {
        testUpdateCheckResult(result)
        if (result.isUpdateAvailable == true) {
            if (result.downloadPath == null) {
                // monero-project: the daemon gives an empty status when the download fails
                logger.warning("TODO Result path is null")
                return
            }
        } else {
            assertNull(result.downloadPath)
        }
    }

    /**
     * Check what every result of the submission of a transaction has.
     */
    fun testSubmitTxResultCommon(result: MoneroSubmitTxResult) {
        assertNotNull(result.isGood)
        assertNotNull(result.isRelayed)
        assertNotNull(result.isDoubleSpend)
        assertNotNull(result.isFeeTooLow)
        assertNotNull(result.isMixinTooLow)
        assertNotNull(result.hasInvalidInput)
        assertNotNull(result.hasInvalidOutput)
        assertNotNull(result.isOverspend)
        assertNotNull(result.isTooBig)
        assertNotNull(result.sanityCheckFailed)
        result.reason?.let { assertTrue(it.isNotEmpty()) }
    }

    /**
     * Check the result of the submission of a transaction that the daemon took.
     */
    fun testSubmitTxResultGood(result: MoneroSubmitTxResult) {
        testSubmitTxResultCommon(result)
        assertEquals(false, result.isDoubleSpend, "tx submission is double spend.")
        assertEquals(false, result.isFeeTooLow, "fee is too low.")
        assertEquals(false, result.isMixinTooLow, "mixin is too low.")
        assertEquals(false, result.hasInvalidInput, "tx has invalid input.")
        assertEquals(false, result.hasInvalidOutput, "tx has invalid output.")
        assertEquals(false, result.hasTooFewOutputs, "tx has too few outputs.")
        assertEquals(false, result.isOverspend, "tx is overspend.")
        assertEquals(false, result.isTooBig, "tx is too big.")
        assertEquals(false, result.sanityCheckFailed, "tx sanity check failed.")

        // 0 credits
        GenAssertions.testUnsignedBigInteger(result.credits, false)
        assertNull(result.topBlockHash)
        assertEquals(false, result.isTxExtraTooBig, "tx extra is too big.")
        assertEquals(true, result.isGood)
        assertEquals(false, result.isNonZeroUnlockTime, "tx has non-zero unlock time.")
    }

    /**
     * Check the result of the submission of a transaction that spends what another one of the pool spends.
     */
    fun testSubmitTxResultDoubleSpend(result: MoneroSubmitTxResult) {
        testSubmitTxResultCommon(result)
        assertEquals(false, result.isGood)
        assertEquals(true, result.isDoubleSpend)
        assertEquals(false, result.isFeeTooLow)
        assertEquals(false, result.isMixinTooLow)
        assertEquals(false, result.hasInvalidInput)
        assertEquals(false, result.hasInvalidOutput)
        assertEquals(false, result.isOverspend)
        assertEquals(false, result.isTooBig)
    }

    /**
     * Check that the daemon gives the status that is expected for each key image, one by one and all together.
     */
    fun testSpentStatuses(daemon: MoneroDaemon, keyImages: List<String>, expectedStatus: MoneroKeyImageSpentStatus) {
        // one image
        for (keyImage in keyImages) assertEquals(expectedStatus, daemon.getKeyImageSpentStatus(keyImage))

        // a list of images
        val statuses = if (keyImages.isNotEmpty()) daemon.getKeyImageSpentStatuses(keyImages) else emptyList()
        assertEquals(keyImages.size, statuses.size)
        for (status in statuses) assertEquals(expectedStatus, status)
    }

    /**
     * Get confirmed transactions of the chain, from the last blocks to the first ones.
     *
     * @param daemon is the daemon to ask
     * @param numTxs is the number of transactions to get
     * @throws AssertionError if the chain has less transactions than that
     */
    fun getConfirmedTxs(daemon: MoneroDaemon, numTxs: Int): List<MoneroTx> {
        val txs = ArrayList<MoneroTx>()
        val numBlocksPerRequest = 50L
        var startIdx = daemon.getHeight() - numBlocksPerRequest - 1

        while (startIdx >= 0) {
            for (block in daemon.getBlocksByRange(startIdx, startIdx + numBlocksPerRequest)) {
                for (tx in block.txs.orEmpty()) {
                    txs.add(tx)
                    if (txs.size == numTxs) return txs
                }
            }
            startIdx -= numBlocksPerRequest
        }

        throw AssertionError("Could not get $numTxs confirmed txs (found: ${txs.size})")
    }

    /**
     * Get the hashes of the confirmed transactions of the last blocks, until it has five, from the last block to the first one.
     */
    fun getConfirmedTxHashes(daemon: MoneroDaemon): MutableList<String> {
        val hashes = ArrayList<String>()
        var height = daemon.getHeight()
        while (hashes.size < 5 && height > 0) {
            height--
            hashes.addAll(daemon.getBlockByHeight(height).txHashes.orEmpty())
        }
        return hashes
    }
}
