package monero.daemon

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import monero.ModelTestSupport
import monero.ModelTestSupport.assertSameJson
import monero.ModelTestSupport.listOf
import monero.ModelTestSupport.read
import monero.ModelTestSupport.tree
import monero.ModelTestSupport.write
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.common.MoneroRpcConnection
import monero.daemon.model.MoneroAddAuxiliaryPowResult
import monero.daemon.model.MoneroAltChain
import monero.daemon.model.MoneroAuxiliaryPow
import monero.daemon.model.MoneroBan
import monero.daemon.model.MoneroBlock
import monero.daemon.model.MoneroBlockHeader
import monero.daemon.model.MoneroBlockTemplate
import monero.daemon.model.MoneroBlocksResult
import monero.daemon.model.MoneroConnectionSpan
import monero.daemon.model.MoneroConnectionType
import monero.daemon.model.MoneroDaemonInfo
import monero.daemon.model.MoneroDaemonNetworkStats
import monero.daemon.model.MoneroDaemonSyncInfo
import monero.daemon.model.MoneroDaemonUpdateCheckResult
import monero.daemon.model.MoneroDaemonUpdateDownloadResult
import monero.daemon.model.MoneroFeeEstimate
import monero.daemon.model.MoneroGenerateBlocksResult
import monero.daemon.model.MoneroGetBlockHashesResult
import monero.daemon.model.MoneroGetBlocksByHashResult
import monero.daemon.model.MoneroHardForkInfo
import monero.daemon.model.MoneroKeyImage
import monero.daemon.model.MoneroMinerData
import monero.daemon.model.MoneroMinerTxSum
import monero.daemon.model.MoneroMiningStatus
import monero.daemon.model.MoneroNetworkType
import monero.daemon.model.MoneroOutput
import monero.daemon.model.MoneroOutputDistributionEntry
import monero.daemon.model.MoneroOutputHistogramEntry
import monero.daemon.model.MoneroPeer
import monero.daemon.model.MoneroPruneResult
import monero.daemon.model.MoneroRpcPaymentInfo
import monero.daemon.model.MoneroSubmitTxResult
import monero.daemon.model.MoneroTx
import monero.daemon.model.MoneroTxBacklogEntry
import monero.daemon.model.MoneroTxPoolStats
import monero.daemon.model.MoneroVersion
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Tag

/**
 * Serialization and deserialization of the daemon data model.
 *
 * The JSON in the fixtures of the daemon was written by monero-c (and so by monero-cpp) from a regtest node, one file
 * for every result that the daemon returns. The models have to read it, and to write it back unchanged. What a regtest
 * node can not produce (peers, bans, alternative chains, a pool with transactions in the histogram, 128-bit values
 * over 64 bits...) is written by hand in the tests, in the same shape.
 *
 * Every test runs without monero-c, a node or the network.
 *
 * It mirrors tests/test_monero_daemon_model.py of monero-python, but not test by test. The fixtures and ModelContractTest read and write
 * every field of every class, so what is here is what a model does besides that. Not ported are the tests of what the parser of monero-cpp
 * does not read back although it writes it (isPruned, isOnline, addressType, isGood, histo, and the peers and the spans of the sync
 * info), because the models are read and written through their fields, and keep all of them.
 */
@Tag("unit")
class MoneroDaemonModelTest {

    private val topHash = "debce41d53f56ad4db3348a73cf52870f7ce18e64b6e961f164282516a803209"
    private val prevHash = "d52697790f85fc3bd5dcdb1334a8a9ea06d07c0d89dee26bc8bace65a01491f2"
    private val seedHash = "418015bb9ae982a1975da7d79277c2705727a56894ba0fb246adaabb1f4632e3"
    private val txHash = "2937db7948bd94f536866c9c02405b147b4dd2bf9d838ddfb8f33963c4926d4b"
    private val minerTxHash = "0f5c276f3df330857d5aac540338dff4908f854b24e88951cde2b0a17ce9b4af"
    private val prunableHash = "f7a26b7989caf255d7b686c0e0b42897bba4dd056c4cdd6054d7af3ecb9f4db7"
    private val two64 = BigInteger.ONE.shiftLeft(64)

    /**
     * The fixtures and the type of the model that every one of them holds.
     */
    private val fixtureTypes = mapOf(
        "alt_chains_empty" to listOf(MoneroAltChain::class.java),
        "block" to MoneroJson.mapper.constructType(MoneroBlock::class.java),
        "block_headers" to listOf(MoneroBlockHeader::class.java),
        "block_template" to MoneroJson.mapper.constructType(MoneroBlockTemplate::class.java),
        "block_with_tx" to MoneroJson.mapper.constructType(MoneroBlock::class.java),
        "blocks" to listOf(MoneroBlock::class.java),
        "blocks_by_hash_result" to MoneroJson.mapper.constructType(MoneroGetBlocksByHashResult::class.java),
        "daemon_info" to MoneroJson.mapper.constructType(MoneroDaemonInfo::class.java),
        "fee_estimate" to MoneroJson.mapper.constructType(MoneroFeeEstimate::class.java),
        "generate_blocks_result" to MoneroJson.mapper.constructType(MoneroGenerateBlocksResult::class.java),
        "hard_fork_info" to MoneroJson.mapper.constructType(MoneroHardForkInfo::class.java),
        "last_block_header" to MoneroJson.mapper.constructType(MoneroBlockHeader::class.java),
        "miner_data" to MoneroJson.mapper.constructType(MoneroMinerData::class.java),
        "miner_tx_sum" to MoneroJson.mapper.constructType(MoneroMinerTxSum::class.java),
        "mining_status" to MoneroJson.mapper.constructType(MoneroMiningStatus::class.java),
        "network_stats" to MoneroJson.mapper.constructType(MoneroDaemonNetworkStats::class.java),
        "output_distribution" to listOf(MoneroOutputDistributionEntry::class.java),
        "output_histogram" to listOf(MoneroOutputHistogramEntry::class.java),
        "peers_empty" to listOf(MoneroPeer::class.java),
        "prune_result" to MoneroJson.mapper.constructType(MoneroPruneResult::class.java),
        "submit_tx_result" to MoneroJson.mapper.constructType(MoneroSubmitTxResult::class.java),
        "sync_info" to MoneroJson.mapper.constructType(MoneroDaemonSyncInfo::class.java),
        "tx_in_block" to MoneroJson.mapper.constructType(MoneroBlocksResult::class.java),
        "tx_in_pool" to MoneroJson.mapper.constructType(MoneroBlocksResult::class.java),
        "tx_pool" to MoneroJson.mapper.constructType(MoneroBlocksResult::class.java),
        "tx_pool_stats" to MoneroJson.mapper.constructType(MoneroTxPoolStats::class.java),
        "tx_pruned_in_block" to MoneroJson.mapper.constructType(MoneroBlocksResult::class.java),
        "version" to MoneroJson.mapper.constructType(MoneroVersion::class.java),
    )

    private fun fixture(name: String) = ModelTestSupport.fixture("daemon", name)

    private fun <T> readFixture(name: String, type: Class<T>): T = MoneroJson.deserialize(fixture(name), type)

    private fun <T> readJson(json: String, type: Class<T>): T = MoneroJson.deserialize(json, type)

    // fixtures: what monero-c writes

    @Test
    fun testFixturesAreAllTested() {
        assertEquals(ModelTestSupport.fixtureNames("daemon"), fixtureTypes.keys.sorted())
    }

    @Test
    fun testFixturesAreJsonWithoutByteOrderMark() {
        for (name in fixtureTypes.keys) {
            val bytes = ModelTestSupport.fixtureFile("daemon", name).readBytes()
            assertFalse(bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte(), name)
            tree(String(bytes, Charsets.UTF_8))
        }
    }

    @TestFactory
    fun testFixtureIsWrittenBackUnchanged() = fixtureTypes.map { (name, type) ->
        dynamicTest(name) {
            val json = fixture(name)
            assertSameJson(json, write(read(json, type)))
        }
    }

    @TestFactory
    fun testFixtureIsWrittenBackUnchangedAfterReadingTheWrittenJson() = fixtureTypes.map { (name, type) ->
        dynamicTest(name) {
            val json = fixture(name)
            val written = write(read(json, type))
            assertSameJson(json, write(read(written, type)))
        }
    }

    @Test
    fun testDaemonInfoReadsTheFixture() {
        val info = readFixture("daemon_info", MoneroDaemonInfo::class.java)
        assertEquals(topHash, info.topBlockHash)
        assertEquals(0L, info.credits)
        assertEquals("0.18.5.1-release", info.version)
        assertEquals(102L, info.height)
        assertEquals(MoneroNetworkType.MAINNET, info.networkType)
        assertEquals(true, info.isRegtest)
        assertEquals(true, info.isSynchronized)
        assertEquals(false, info.isRestricted)
        assertEquals(1, info.numTxs)
        assertEquals(BigInteger.ONE, info.difficulty)
        assertEquals(BigInteger.valueOf(102), info.cumulativeDifficulty)
        assertEquals(86356631552L, info.freeSpace)
        assertNull(info.bootstrapDaemonAddress)
    }

    @Test
    fun testDaemonSyncInfoReadsTheFixture() {
        val info = readFixture("sync_info", MoneroDaemonSyncInfo::class.java)
        assertEquals(102L, info.height)
        assertEquals(0L, info.targetHeight)
        assertEquals(1, info.nextNeededPruningSeed)
        assertNull(info.peers)
        assertNull(info.spans)
    }

    @Test
    fun testHardForkInfoReadsTheFixture() {
        val info = readFixture("hard_fork_info", MoneroHardForkInfo::class.java)
        assertEquals(1L, info.earliestHeight)
        assertEquals(true, info.isEnabled)
        assertEquals(0, info.state)
        assertEquals(16, info.version)
        assertEquals(101, info.numVotes)
        assertEquals(102, info.window)
        assertEquals(16, info.voting)
    }

    @Test
    fun testFeeEstimateReadsTheFixture() {
        val estimate = readFixture("fee_estimate", MoneroFeeEstimate::class.java)
        assertEquals(BigInteger.valueOf(1200000), estimate.fee)
        assertEquals(10000L, estimate.quantizationMask)
        assertEquals(4, estimate.fees!!.size)
        assertEquals(BigInteger.valueOf(4700000), estimate.fees!![1])
    }

    @Test
    fun testMiningStatusReadsTheFixture() {
        val status = readFixture("mining_status", MoneroMiningStatus::class.java)
        assertEquals(false, status.isActive)
        assertEquals(0L, status.speed)
        assertEquals(0, status.numThreads)
        assertNull(status.isBackground)
        assertNull(status.address)
    }

    @Test
    fun testMinerTxSumReadsTheFixture() {
        val sum = readFixture("miner_tx_sum", MoneroMinerTxSum::class.java)
        assertEquals(BigInteger.valueOf(351840365463352), sum.emissionSum)
        assertEquals(BigInteger.ZERO, sum.feeSum)
    }

    @Test
    fun testPruneResultReadsTheFixture() {
        val result = readFixture("prune_result", MoneroPruneResult::class.java)
        assertEquals(false, result.isPruned)
        assertEquals(0, result.pruningSeed)
    }

    @Test
    fun testSubmitTxResultReadsTheFixture() {
        val result = readFixture("submit_tx_result", MoneroSubmitTxResult::class.java)
        assertEquals(true, result.isGood)
        assertEquals(true, result.isRelayed)
        assertEquals(false, result.isDoubleSpend)
        assertEquals(false, result.sanityCheckFailed)
        assertNull(result.reason)
    }

    @Test
    fun testGenerateBlocksResultReadsTheFixture() {
        val result = readFixture("generate_blocks_result", MoneroGenerateBlocksResult::class.java)
        assertEquals(101L, result.height)
        assertEquals(listOf(topHash), result.blockHashes)
    }

    @Test
    fun testVersionReadsTheFixture() {
        val version = readFixture("version", MoneroVersion::class.java)
        assertEquals(196624, version.number)
        assertEquals(true, version.isRelease)
    }

    @Test
    fun testNetworkStatsReadsTheFixture() {
        val stats = readFixture("network_stats", MoneroDaemonNetworkStats::class.java)
        assertEquals(1791736227L, stats.startTime)
        assertEquals(0L, stats.totalPacketsIn)
        assertEquals(0L, stats.totalBytesOut)
    }

    @Test
    fun testTxPoolStatsReadsTheFixture() {
        val stats = readFixture("tx_pool_stats", MoneroTxPoolStats::class.java)
        assertEquals(1, stats.numTxs)
        assertEquals(0, stats.numNotRelayed)
        assertEquals(BigInteger.valueOf(2599200000), stats.feeTotal)
        assertEquals(2166L, stats.bytesMax)
        assertEquals(2166L, stats.bytesTotal)
        assertEquals(1791736242L, stats.oldestTimestamp)
        assertEquals(emptyMap(), stats.histo)
    }

    @Test
    fun testOutputHistogramReadsTheFixture() {
        val entries = MoneroJson.deserialize<List<MoneroOutputHistogramEntry>>(fixture("output_histogram"))
        assertEquals(2, entries.size)
        assertEquals(BigInteger.ZERO, entries[0].amount)
        assertEquals(103L, entries[0].numInstances)
        assertEquals(BigInteger.valueOf(17592186044415), entries[1].amount)
        assertEquals(1L, entries[1].numInstances)
    }

    @Test
    fun testOutputDistributionReadsTheFixture() {
        val entries = MoneroJson.deserialize<List<MoneroOutputDistributionEntry>>(fixture("output_distribution"))
        assertEquals(1, entries.size)
        assertEquals(BigInteger.ZERO, entries[0].amount)
        assertEquals(0L, entries[0].base)
        assertEquals(0L, entries[0].startHeight)
        assertEquals(102, entries[0].distribution!!.size)
        assertEquals(listOf(0L, 1L, 1L), entries[0].distribution!!.take(3))
    }

    @Test
    fun testEmptyListsAreReadAsEmptyLists() {
        assertEquals(emptyList(), MoneroJson.deserialize<List<MoneroAltChain>>(fixture("alt_chains_empty")))
        assertEquals(emptyList(), MoneroJson.deserialize<List<MoneroPeer>>(fixture("peers_empty")))
    }

    @Test
    fun testBlockHeaderReadsTheFixture() {
        val header = readFixture("last_block_header", MoneroBlockHeader::class.java)
        assertEquals(101L, header.height)
        assertEquals(topHash, header.hash)
        assertEquals(prevHash, header.prevHash)
        assertEquals(minerTxHash, header.minerTxHash)
        assertEquals(BigInteger.valueOf(35180227487959), header.reward)
        assertEquals(1, header.numTxs)
        assertEquals(16, header.majorVersion)
        assertEquals(0L, header.nonce)
        assertEquals(false, header.orphanStatus)
        assertEquals(BigInteger.ONE, header.difficulty)
        assertEquals(BigInteger.valueOf(102), header.cumulativeDifficulty)
        assertNull(header.powHash)
    }

    @Test
    fun testBlockHeadersReadTheFixture() {
        val headers = MoneroJson.deserialize<List<MoneroBlockHeader>>(fixture("block_headers"))
        assertEquals(3L, headers.first().height)
        for (i in 1 until headers.size) {
            assertEquals(headers[i].height!! - 1, headers[i - 1].height)
            assertEquals(headers[i - 1].hash, headers[i].prevHash)
        }
    }

    @Test
    fun testBlockTemplateReadsTheFixture() {
        val template = readFixture("block_template", MoneroBlockTemplate::class.java)
        assertEquals(101L, template.height)
        assertEquals(prevHash, template.prevHash)
        assertEquals(seedHash, template.seedHash)
        assertEquals("", template.nextSeedHash)
        assertEquals(BigInteger.valueOf(35180227487959), template.expectedReward)
        assertEquals(BigInteger.ONE, template.difficulty)
        assertEquals(0L, template.reservedOffset)
        assertNotNull(template.blockTemplateBlob)
        assertNotNull(template.blockHashingBlob)
    }

    @Test
    fun testMinerDataReadsTheFixture() {
        val data = readFixture("miner_data", MoneroMinerData::class.java)
        assertEquals(16, data.majorVersion)
        assertEquals(101L, data.height)
        assertEquals(prevHash, data.prevHash)
        assertEquals(seedHash, data.seedHash)
        assertEquals("0x1", data.difficulty)
        assertEquals(300000L, data.medianWeight)
        assertEquals(BigInteger.valueOf(3535693871620981), data.alreadyGeneratedCoins)
        assertEquals(1, data.txPoolBacklog!!.size)
        assertEquals(txHash, data.txPoolBacklog!![0].hash)
        assertEquals(BigInteger.valueOf(2599200000), data.txPoolBacklog!![0].fee)
        assertEquals(2166L, data.txPoolBacklog!![0].weight)
    }

    @Test
    fun testBlockReadsTheFixture() {
        val block = readFixture("block", MoneroBlock::class.java)
        assertEquals(5L, block.height)
        assertNotNull(block.hex)
        assertNull(block.txs)
        assertNull(block.txHashes)

        val minerTx = block.minerTx!!
        assertEquals(true, minerTx.isMinerTx)
        assertSame(block, minerTx.block)
        assertEquals(5L, minerTx.height)
        assertEquals(1, minerTx.outputs!!.size)
        assertEquals(BigInteger.valueOf(35184070099967), minerTx.outputs!![0].amount)
        assertSame(minerTx, minerTx.outputs!![0].tx)
        assertEquals(36, minerTx.extra!!.size)
    }

    @Test
    fun testBlockWithTxHashesReadsTheFixture() {
        val block = readFixture("block_with_tx", MoneroBlock::class.java)
        assertEquals(101L, block.height)
        assertEquals(listOf(txHash), block.txHashes)
        assertNull(block.txs)
        assertEquals(minerTxHash, block.minerTxHash)
    }

    @Test
    fun testBlocksReadTheFixture() {
        val blocks = MoneroJson.deserialize<List<MoneroBlock>>(fixture("blocks"))
        assertEquals(listOf(99L, 100L, 101L), blocks.map { it.height })
        for (block in blocks) assertSame(block, block.minerTx!!.block)
    }

    @Test
    fun testBlocksByHashResultReadsTheFixture() {
        val result = readFixture("blocks_by_hash_result", MoneroGetBlocksByHashResult::class.java)
        assertEquals(102L, result.currentHeight)
        assertEquals(1L, result.blocks!!.first().height)
        assertTrue(result.blocks!!.all { it.minerTx!!.block === it })
    }

    @Test
    fun testTxInBlockReadsTheFixture() {
        val result = readFixture("tx_in_block", MoneroBlocksResult::class.java)
        assertEquals(1, result.blocks!!.size)
        val block = result.blocks!![0]
        assertEquals(101L, block.height)
        assertEquals(1791736242L, block.timestamp)

        val tx = block.txs!![0]
        assertSame(block, tx.block)
        assertEquals(listOf(tx), result.getTxs())
        assertEquals(txHash, tx.hash)
        assertEquals(101L, tx.height)
        assertEquals(2, tx.version)
        assertEquals(BigInteger.valueOf(2599200000), tx.fee)
        assertEquals(BigInteger.ZERO, tx.unlockTime)
        assertEquals(true, tx.isConfirmed)
        assertEquals(false, tx.inTxPool)
        assertEquals(prunableHash, tx.prunableHash)
        assertNotNull(tx.fullHex)
        assertNull(tx.prunedHex)
        assertEquals(listOf(101L, 102L), tx.outputIndices)
        assertEquals(44, tx.extra!!.size)

        assertEquals(2, tx.inputs!!.size)
        for (input in tx.inputs!!) {
            assertSame(tx, input.tx)
            assertEquals(BigInteger.ZERO, input.amount)
            assertNotNull(input.keyImage!!.hex)
            assertEquals(16, input.ringOutputIndices!!.size)
        }
        assertEquals(listOf(101L, 102L), tx.outputs!!.map { it.index })
        for (output in tx.outputs!!) {
            assertSame(tx, output.tx)
            assertNotNull(output.stealthPublicKey)
            assertNotNull(output.mask)
        }
    }

    @Test
    fun testTxInPoolReadsTheFixtureWithABlockThatHasNoHeight() {
        val result = readFixture("tx_in_pool", MoneroBlocksResult::class.java)
        val block = result.blocks!![0]
        assertNull(block.height)
        assertNull(block.timestamp)

        val tx = block.txs!![0]
        assertSame(block, tx.block)
        assertNull(tx.height)
        assertEquals(txHash, tx.hash)
        assertEquals(true, tx.inTxPool)
        assertEquals(false, tx.isConfirmed)
        assertEquals(0L, tx.numConfirmations)
        assertEquals(1791736242L, tx.receivedTimestamp)
    }

    @Test
    fun testTxPoolReadsTheFixture() {
        val txs = readFixture("tx_pool", MoneroBlocksResult::class.java).getTxs()
        assertEquals(1, txs.size)
        val tx = txs[0]
        assertEquals(false, tx.isRelayed)
        assertEquals(false, tx.isKeptByBlock)
        assertEquals(41L, tx.maxUsedBlockHeight)
        assertEquals(2166L, tx.size)
        assertEquals(2166L, tx.weight)
        assertNotNull(tx.maxUsedBlockHash)
    }

    @Test
    fun testPrunedTxReadsTheFixture() {
        val tx = readFixture("tx_pruned_in_block", MoneroBlocksResult::class.java).getTxs()[0]
        assertNotNull(tx.prunedHex)
        assertNull(tx.fullHex)
        assertNull(tx.prunableHex)
        assertEquals(prunableHash, tx.prunableHash)
    }

    // what a regtest node can not produce

    @Test
    fun testPeerIsReadAndWritten() {
        val json = """{"id":"a1b2","address":"1.2.3.4:18080","host":"1.2.3.4","port":18080,"isOnline":true,"lastSeenTimestamp":1700000000,
            "pruningSeed":3,"rpcPort":18081,"rpcCreditsPerHash":7,"hash":"h","avgDownload":10,"avgUpload":11,"currentDownload":12,
            "currentUpload":13,"height":3000000,"isIncoming":false,"liveTime":99,"isLocalIp":false,"isLocalHost":false,"numReceives":5,
            "numSends":6,"receiveIdleTime":7,"sendIdleTime":8,"state":"normal","numSupportFlags":1,"addressType":1}"""
        val peer = readJson(json, MoneroPeer::class.java)
        assertEquals("1.2.3.4:18080", peer.address)
        assertEquals(18080, peer.port)
        assertEquals(3000000L, peer.height)
        assertEquals(MoneroConnectionType.IPV4, peer.connectionType)
        assertSameJson(json, write(peer))
    }

    @Test
    fun testPeerConnectionTypeIsTheNumberOfAddressType() {
        val expected = mapOf(0 to MoneroConnectionType.INVALID, 1 to MoneroConnectionType.IPV4, 2 to MoneroConnectionType.IPV6,
            3 to MoneroConnectionType.TOR, 4 to MoneroConnectionType.I2P)
        for ((number, type) in expected) {
            val peer = readJson("""{"addressType":$number}""", MoneroPeer::class.java)
            assertEquals(type, peer.connectionType)
            assertEquals("""{"addressType":$number}""", write(peer))
        }
    }

    @Test
    fun testPeerWithoutAddressTypeHasNoConnectionType() {
        assertNull(readJson("""{"address":"a"}""", MoneroPeer::class.java).connectionType)
    }

    @Test
    fun testSyncInfoWithPeersAndSpansIsReadAndWritten() {
        val json = """{"credits":4,"topBlockHash":"t","height":3000000,"targetHeight":3000100,"nextNeededPruningSeed":2,
            "overview":"..oom","peers":[{"address":"1.2.3.4:18080","height":3000000}],
            "spans":[{"connectionId":"c","numBlocks":20,"remoteAddress":"1.2.3.4:18080","rate":1,"speed":2,"size":3,"startHeight":2999990}]}"""
        val info = readJson(json, MoneroDaemonSyncInfo::class.java)
        assertEquals("1.2.3.4:18080", info.peers!![0].address)
        assertEquals(2999990L, info.spans!![0].startHeight)
        assertEquals(4L, info.credits)
        assertSameJson(json, write(info))
    }

    @Test
    fun testConnectionSpanIsReadAndWritten() {
        val json = """{"connectionId":"c","numBlocks":20,"remoteAddress":"r","rate":1,"speed":2,"size":3,"startHeight":4}"""
        assertSameJson(json, write(readJson(json, MoneroConnectionSpan::class.java)))
    }

    @Test
    fun testBanIsReadAndWritten() {
        val json = """{"host":"192.168.1.100","ip":1684279488,"isBanned":true,"seconds":3600}"""
        val ban = readJson(json, MoneroBan::class.java)
        assertEquals("192.168.1.100", ban.host)
        assertEquals(true, ban.isBanned)
        assertEquals(3600L, ban.seconds)
        assertSameJson(json, write(ban))
    }

    @Test
    fun testBanIpIsAnUnsigned32BitNumber() {
        val ban = readJson("""{"ip":4294967295}""", MoneroBan::class.java)
        assertEquals(4294967295L, ban.ip)
        assertEquals("""{"ip":4294967295}""", write(ban))
    }

    @Test
    fun testBansAreReadAndWrittenAsAList() {
        val json = """[{"host":"a","isBanned":true},{"host":"b","isBanned":false,"seconds":0}]"""
        val bans = MoneroJson.deserialize<List<MoneroBan>>(json)
        assertEquals(listOf("a", "b"), bans.map { it.host })
        assertSameJson(json, write(bans))
    }

    @Test
    fun testAltChainIsReadAndWritten() {
        val json = """[{"blockHashes":["a","b"],"difficultyLow":10,"difficultyHigh":1,"length":2,"height":3000000,"mainChainParentBlockHash":"p"}]"""
        val chains = MoneroJson.deserialize<List<MoneroAltChain>>(json)
        assertEquals(listOf("a", "b"), chains[0].blockHashes)
        assertEquals(two64.add(BigInteger.TEN), chains[0].difficulty)
        assertEquals("p", chains[0].mainChainParentBlockHash)
        assertSameJson(json, write(chains))
    }

    @Test
    fun testUpdateCheckResultIsReadAndWritten() {
        val json = """{"isUpdateAvailable":true,"version":"0.18.6.0","hash":"h","autoUri":"https://a","userUri":"https://u"}"""
        val result = readJson(json, MoneroDaemonUpdateCheckResult::class.java)
        assertEquals(true, result.isUpdateAvailable)
        assertEquals("0.18.6.0", result.version)
        assertSameJson(json, write(result))
    }

    @Test
    fun testUpdateDownloadResultIsAnUpdateCheckResultWithThePathOfTheDownload() {
        val json = """{"isUpdateAvailable":true,"version":"v","downloadPath":"/tmp/monero.tar.bz2"}"""
        val result = readJson(json, MoneroDaemonUpdateDownloadResult::class.java)
        assertEquals("/tmp/monero.tar.bz2", result.downloadPath)
        assertEquals(true, (result as MoneroDaemonUpdateCheckResult).isUpdateAvailable)
        assertSameJson(json, write(result))
    }

    @Test
    fun testAddAuxiliaryPowResultIsReadAndWritten() {
        val json = """{"credits":1,"topBlockHash":"t","blockTemplateBlob":"b","blockHashingBlob":"h","merkleRoot":"m","merkleTreeDepth":3,
            "auxPow":[{"id":"i","hash":"x"},{"id":"j","hash":"y"}]}"""
        val result = readJson(json, MoneroAddAuxiliaryPowResult::class.java)
        assertEquals(3, result.merkleTreeDepth)
        assertEquals(listOf("i", "j"), result.auxPow!!.map { it.id })
        assertEquals(1L, result.credits)
        assertSameJson(json, write(result))
    }

    @Test
    fun testTxBacklogEntryIsAnEmptyObject() {
        val entries = MoneroJson.deserialize<List<MoneroTxBacklogEntry>>("[{},{}]")
        assertEquals(2, entries.size)
        assertEquals("[{},{}]", write(entries))
    }

    @Test
    fun testKeyImageIsReadAndWritten() {
        val json = """{"hex":"abc","signature":"def"}"""
        val keyImage = readJson(json, MoneroKeyImage::class.java)
        assertEquals("abc", keyImage.hex)
        assertEquals("def", keyImage.signature)
        assertSameJson(json, write(keyImage))
    }

    @Test
    fun testTxPoolStatsWithTransactionsHasTheHistogram() {
        val json = """{"numTxs":3,"histo":{"1500":2,"4000":1},"histo98pc":600,"num10m":1}"""
        val stats = readJson(json, MoneroTxPoolStats::class.java)
        assertEquals(mapOf(1500L to 2L, 4000L to 1L), stats.histo)
        assertEquals(600L, stats.histo98pc)
        assertEquals(1, stats.num10m)
        assertSameJson(json, write(stats))
    }

    @Test
    fun testDaemonInfoWithEveryOptionalFieldIsReadAndWritten() {
        val json = """{"credits":9,"topBlockHash":"t","version":"0.18.5.1","numAltBlocks":2,"blockSizeLimit":600000,"blockSizeMedian":300000,
            "blockWeightLimit":600000,"blockWeightMedian":300000,"bootstrapDaemonAddress":"node.example:18081","difficultyLow":5,"difficultyHigh":6,
            "cumulativeDifficultyLow":7,"cumulativeDifficultyHigh":8,"freeSpace":1,"numOfflinePeers":2,"numOnlinePeers":3,"height":3000000,
            "heightWithoutBootstrap":2999999,"networkType":2,"isOffline":false,"numIncomingConnections":4,"numOutgoingConnections":5,
            "numRpcConnections":6,"startTimestamp":1,"adjustedTimestamp":2,"target":120,"targetHeight":3,"numTxs":7,"numTxsPool":8,
            "wasBootstrapEverUsed":true,"databaseSize":9,"updateAvailable":false,"isBusySyncing":true,"isSynchronized":false,"isRestricted":true,
            "isRegtest":false}"""
        val info = readJson(json, MoneroDaemonInfo::class.java)
        assertEquals(MoneroNetworkType.STAGENET, info.networkType)
        assertEquals("node.example:18081", info.bootstrapDaemonAddress)
        assertSameJson(json, write(info))
    }

    @Test
    fun testMiningStatusWhenMiningIsReadAndWritten() {
        val json = """{"isActive":true,"isBackground":false,"address":"4abc","speed":1500,"numThreads":4}"""
        val status = readJson(json, MoneroMiningStatus::class.java)
        assertEquals("4abc", status.address)
        assertEquals(1500L, status.speed)
        assertSameJson(json, write(status))
    }

    @Test
    fun testSubmitTxResultWhenRejectedHasTheReason() {
        val json = """{"isGood":false,"isRelayed":false,"isDoubleSpend":true,"isFeeTooLow":true,"isMixinTooLow":true,"hasInvalidInput":true,
            "hasInvalidOutput":true,"hasTooFewOutputs":true,"isOverspend":true,"isTooBig":true,"sanityCheckFailed":true,"reason":"double spend",
            "isTxExtraTooBig":true,"isNonZeroUnlockTime":true}"""
        val result = readJson(json, MoneroSubmitTxResult::class.java)
        assertEquals(false, result.isGood)
        assertEquals("double spend", result.reason)
        assertSameJson(json, write(result))
    }

    @Test
    fun testBlockHeaderWithTheProofOfWorkHashIsReadAndWritten() {
        val json = """{"height":1,"powHash":"abc"}"""
        val header = readJson(json, MoneroBlockHeader::class.java)
        assertEquals("abc", header.powHash)
        assertSameJson(json, write(header))
    }

    @Test
    fun testTxWithEveryFieldIsReadAndWritten() {
        val json = """{"hash":"h","version":2,"isMinerTx":false,"paymentId":"0000000000000000","fee":1,"ringSize":16,"relay":true,
            "isRelayed":true,"isConfirmed":true,"inTxPool":false,"isLocked":false,"numConfirmations":10,"unlockTime":18446744073709551615,
            "lastRelayedTimestamp":1,"receivedTimestamp":2,"isDoubleSpendSeen":false,"key":"k","fullHex":"f","prunedHex":"p","prunableHex":"q",
            "prunableHash":"r","size":3,"weight":4,"inputs":[{"amount":0,"keyImage":{"hex":"i"},"ringOutputIndices":[1,2]}],
            "outputs":[{"amount":0,"index":5,"stealthPublicKey":"s","mask":"m"}],"outputIndices":[5],"metadata":"md","commonTxSets":"c",
            "extra":[1,2,3],"rctSignatures":"rs","rctSigPrunable":"rp","isKeptByBlock":false,"isFailed":false,"lastFailedHeight":6,
            "lastFailedHash":"lf","maxUsedBlockHeight":7,"maxUsedBlockHash":"mu","signatures":["a","b"]}"""
        val tx = readJson(json, MoneroTx::class.java)
        assertEquals(two64.subtract(BigInteger.ONE), tx.unlockTime)
        assertSameJson(json, write(tx))
    }

    @Test
    fun testOutputWithEveryFieldIsReadAndWritten() {
        val json = """{"keyImage":{"hex":"k","signature":"s"},"amount":18446744073709551615,"index":3,"ringOutputIndices":[1,2,3],
            "stealthPublicKey":"p","mask":"m"}"""
        val output = readJson(json, MoneroOutput::class.java)
        assertEquals(two64.subtract(BigInteger.ONE), output.amount)
        assertSameJson(json, write(output))
    }

    @Test
    fun testBlocksResultGetTxsListsTheTxsInTheOrderOfTheBlocks() {
        val json = """{"blocks":[{"height":1,"txs":[{"hash":"a"},{"hash":"b"}]},{"height":2,"txs":[{"hash":"c"}]},{"txs":[{"hash":"d"}]}]}"""
        val result = readJson(json, MoneroBlocksResult::class.java)
        assertEquals(listOf("a", "b", "c", "d"), result.getTxs().map { it.hash })
        assertEquals(listOf(1L, 1L, 2L, null), result.getTxs().map { it.height })
    }

    @Test
    fun testBlocksResultGetTxsIsEmptyWithoutBlocks() {
        assertEquals(emptyList(), readJson("""{"blocks":[]}""", MoneroBlocksResult::class.java).getTxs())
        assertEquals(emptyList(), readJson("{}", MoneroBlocksResult::class.java).getTxs())
        assertEquals(emptyList(), readJson("""{"blocks":[{"height":1}]}""", MoneroBlocksResult::class.java).getTxs())
    }

    @Test
    fun testBlocksResultGetTxsReturnsANewListEveryTime() {
        val result = readJson("""{"blocks":[{"txs":[{"hash":"a"}]}]}""", MoneroBlocksResult::class.java)
        val first = result.getTxs() as MutableList<MoneroTx>
        first.clear()
        assertEquals(1, result.getTxs().size)
    }

    @Test
    fun testGetBlockHashesResultIsReadAndWritten() {
        val json = """{"startHeight":10,"currentHeight":20,"hashes":["a","b"]}"""
        val result = readJson(json, MoneroGetBlockHashesResult::class.java)
        assertEquals(10L, result.startHeight)
        assertEquals(listOf("a", "b"), result.hashes)
        assertSameJson(json, write(result))
    }

    // 128-bit numbers: two halves in the JSON

    @Test
    fun testDifficultyIsWrittenAsTwoHalvesAndReadFromThem() {
        val values = listOf(
            BigInteger.ZERO to ("0" to "0"),
            BigInteger.ONE to ("1" to "0"),
            two64.subtract(BigInteger.ONE) to ("18446744073709551615" to "0"),
            two64 to ("0" to "1"),
            BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE) to ("18446744073709551615" to "18446744073709551615"),
            two64.multiply(BigInteger.valueOf(3)).add(BigInteger.valueOf(7)) to ("7" to "3"),
        )
        for ((value, halves) in values) {
            val header = MoneroBlockHeader()
            header.difficulty = value
            assertSameJson("""{"difficultyLow":${halves.first},"difficultyHigh":${halves.second}}""", write(header))

            val read = readJson("""{"difficultyLow":${halves.first},"difficultyHigh":${halves.second}}""", MoneroBlockHeader::class.java)
            assertEquals(value, read.difficulty)
        }
    }

    @Test
    fun testDifficultySetToNullClearsBothHalves() {
        val header = readJson("""{"difficultyLow":5,"difficultyHigh":6}""", MoneroBlockHeader::class.java)
        header.difficulty = null
        assertNull(header.difficultyLow)
        assertNull(header.difficultyHigh)
        assertEquals("{}", write(header))
    }

    @Test
    fun testDifficultyWithOnlyOneHalfInTheJsonIsStillAValue() {
        assertEquals(BigInteger.valueOf(5), readJson("""{"difficultyLow":5}""", MoneroBlockHeader::class.java).difficulty)
        assertEquals(two64.multiply(BigInteger.valueOf(6)), readJson("""{"difficultyHigh":6}""", MoneroBlockHeader::class.java).difficulty)
        assertNull(readJson("{}", MoneroBlockHeader::class.java).difficulty)
    }

    @Test
    fun testDifficultyIsInheritedByTheBlock() {
        val block = readJson("""{"difficultyLow":1,"difficultyHigh":1,"cumulativeDifficultyLow":2,"cumulativeDifficultyHigh":0}""", MoneroBlock::class.java)
        assertEquals(two64.add(BigInteger.ONE), block.difficulty)
        assertEquals(BigInteger.valueOf(2), block.cumulativeDifficulty)
    }

    @Test
    fun testDifficultyThatDoesNotFitIn128BitsIsRefused() {
        val header = MoneroBlockHeader()
        for (value in listOf(BigInteger.ONE.shiftLeft(128), BigInteger.valueOf(-1))) {
            kotlin.test.assertFailsWith<IllegalArgumentException>("$value") { header.difficulty = value }
        }
        assertNull(header.difficultyLow)
    }

    @Test
    fun testOtherSumsAreSplitInTwoHalvesToo() {
        val sum = MoneroMinerTxSum()
        sum.emissionSum = two64.add(BigInteger.valueOf(9))
        sum.feeSum = BigInteger.valueOf(4)
        assertSameJson("""{"emissionSumLow":9,"emissionSumHigh":1,"feeSumLow":4,"feeSumHigh":0}""", write(sum))

        val template = MoneroBlockTemplate()
        template.difficulty = BigInteger.TEN
        assertEquals(BigInteger.TEN, template.difficultyLow)

        val info = MoneroDaemonInfo()
        info.cumulativeDifficulty = two64
        assertEquals(BigInteger.ONE, info.cumulativeDifficultyHigh)

        val chain = MoneroAltChain()
        chain.difficulty = BigInteger.TWO
        assertEquals(BigInteger.TWO, chain.difficultyLow)
    }

    // parents: a block holds its txs, a tx its inputs and its outputs

    @Test
    fun testBlockSetsItselfAsTheBlockOfItsTxsAndOfItsMinerTx() {
        val block = MoneroBlock()
        val tx1 = MoneroTx()
        val tx2 = MoneroTx()
        val minerTx = MoneroTx()
        block.txs = listOf(tx1, tx2)
        block.minerTx = minerTx
        assertSame(block, tx1.block)
        assertSame(block, tx2.block)
        assertSame(block, minerTx.block)

        block.height = 12
        assertEquals(12L, tx1.height)
    }

    @Test
    fun testTxSetsItselfAsTheTxOfItsInputsAndOutputs() {
        val tx = MoneroTx()
        val input = MoneroOutput()
        val output = MoneroOutput()
        tx.inputs = listOf(input)
        tx.outputs = listOf(output)
        assertSame(tx, input.tx)
        assertSame(tx, output.tx)
    }

    @Test
    fun testTxWhenItsListsAreReplacedOrClearedIsStillSafe() {
        val tx = MoneroTx()
        tx.outputs = listOf(MoneroOutput())
        tx.outputs = null
        tx.outputs = emptyList()
        tx.inputs = null
        val block = MoneroBlock()
        block.txs = listOf(tx)
        block.txs = null
        block.minerTx = null
        assertNull(block.txs)
        assertNull(block.minerTx)
    }

    @Test
    fun testTxHeightIsNullWithoutABlock() {
        assertNull(MoneroTx().height)
        val tx = MoneroTx()
        tx.block = MoneroBlock()
        assertNull(tx.height)
    }

    @Test
    fun testParentsAreSetByTheDeserializerWhateverTheOrderOfTheFields() {
        val json = """{"txs":[{"outputs":[{"amount":1}],"inputs":[{"index":2}],"hash":"a"}],"minerTx":{"hash":"m","outputs":[{"amount":2}]},"height":7}"""
        val block = readJson(json, MoneroBlock::class.java)
        val tx = block.txs!![0]
        assertSame(block, tx.block)
        assertSame(tx, tx.outputs!![0].tx)
        assertSame(tx, tx.inputs!![0].tx)
        assertSame(block, block.minerTx!!.block)
        assertSame(block.minerTx, block.minerTx!!.outputs!![0].tx)
        assertEquals(7L, tx.height)
    }

    @Test
    fun testParentsAreNotInTheJsonSoAGraphIsWrittenWithoutCycles() {
        val block = MoneroBlock()
        val tx = MoneroTx()
        val output = MoneroOutput()
        tx.hash = "a"
        output.amount = BigInteger.ONE
        tx.outputs = listOf(output)
        block.txs = listOf(tx)
        block.minerTx = tx
        assertSame(block, tx.block)
        assertSameJson("""{"txs":[{"hash":"a","outputs":[{"amount":1}]}],"minerTx":{"hash":"a","outputs":[{"amount":1}]}}""", write(block))
    }

    @Test
    fun testBlockWithManyTxsIsReadAndWritten() {
        val txs = (0 until 2000).joinToString(",") { """{"hash":"tx$it","fee":$it,"outputs":[{"amount":$it}]}""" }
        val json = """{"height":5,"txs":[$txs]}"""
        val block = readJson(json, MoneroBlock::class.java)
        assertEquals(2000, block.txs!!.size)
        assertTrue(block.txs!!.all { it.block === block })
        assertSameJson(json, write(block))
    }

    // copies

    @Test
    fun testTxCopyKeepsThePrunedHexAndThePrunableHexApart() {
        val tx = MoneroTx()
        tx.prunedHex = "pruned"
        tx.prunableHex = "prunable"
        tx.fullHex = "full"
        val copy = tx.copy()
        assertEquals("pruned", copy.prunedHex)
        assertEquals("prunable", copy.prunableHex)
        assertEquals("full", copy.fullHex)
    }

    @Test
    fun testTxCopyIsDeep() {
        val tx = readFixture("tx_in_block", MoneroBlocksResult::class.java).getTxs()[0]
        val copy = tx.copy()
        assertNotSame(tx.inputs!![0], copy.inputs!![0])
        assertNotSame(tx.inputs!![0].keyImage, copy.inputs!![0].keyImage)
        assertNotSame(tx.outputs!![0], copy.outputs!![0])
        assertNotSame(tx.outputIndices, copy.outputIndices)
        assertNotSame(tx.extra, copy.extra)
        assertSameJson(write(tx), write(copy))

        copy.inputs!![0].keyImage!!.hex = "changed"
        assertTrue(tx.inputs!![0].keyImage!!.hex != "changed")
    }

    @Test
    fun testTxCopyIsTheParentOfItsOwnOutputsAndHasNoBlock() {
        val tx = readFixture("tx_in_block", MoneroBlocksResult::class.java).getTxs()[0]
        val copy = tx.copy()
        assertNull(copy.block)
        assertNull(copy.height)
        for (output in copy.outputs!!) assertSame(copy, output.tx)
        for (input in copy.inputs!!) assertSame(copy, input.tx)
        for (output in tx.outputs!!) assertSame(tx, output.tx)
    }

    @Test
    fun testBlockCopyIsDeepAndIsTheParentOfItsOwnTxs() {
        val block = readFixture("tx_in_block", MoneroBlocksResult::class.java).blocks!![0]
        block.minerTx = MoneroTx()
        val copy = block.copy()
        assertNotSame(block.txs, copy.txs)
        assertNotSame(block.txs!![0], copy.txs!![0])
        assertNotSame(block.minerTx, copy.minerTx)
        assertSame(copy, copy.txs!![0].block)
        assertSame(copy, copy.minerTx!!.block)
        assertSame(block, block.txs!![0].block)
        assertEquals(101L, copy.txs!![0].height)
        assertSameJson(write(block), write(copy))
    }

    @Test
    fun testCopyIsOfTheSameClassWhateverTheTypeItIsCalledOn() {
        val header: MoneroBlockHeader = MoneroBlock()
        assertEquals(MoneroBlock::class, header.copy()::class)
        val result: MoneroDaemonUpdateCheckResult = MoneroDaemonUpdateDownloadResult()
        assertEquals(MoneroDaemonUpdateDownloadResult::class, result.copy()::class)
        val output: MoneroOutput = MoneroOutput()
        assertEquals(MoneroOutput::class, output.copy()::class)
    }

    @Test
    fun testKeyImageCopyIsIndependent() {
        val keyImage = MoneroKeyImage()
        keyImage.hex = "a"
        val copy = keyImage.copy()
        copy.hex = "b"
        assertEquals("a", keyImage.hex)
    }

    // ------------------------------- values that are not valid ---------------------------------

    @Test
    fun testPeerWithAnAddressTypeThatIsNotOneIsRefused() {
        for (number in listOf(5, -1, 255)) {
            val error = assertFailsWith<MoneroError>("$number") { readJson("""{"addressType":$number}""", MoneroPeer::class.java) }
            assertTrue("MoneroPeer" in error.message!!, error.message)
        }
    }

    @Test
    fun testDaemonInfoWithANetworkTypeThatIsNotOneIsRefused() {
        for (number in listOf(4, 9, -1)) {
            val error = assertFailsWith<MoneroError>("$number") { readJson("""{"networkType":$number}""", MoneroDaemonInfo::class.java) }
            assertTrue("MoneroDaemonInfo" in error.message!!, error.message)
        }
    }

    @Test
    fun testDaemonInfoNetworkTypeIsTheNumberOfTheNetwork() {
        val expected = mapOf(0 to MoneroNetworkType.MAINNET, 1 to MoneroNetworkType.TESTNET, 2 to MoneroNetworkType.STAGENET, 3 to MoneroNetworkType.REGTEST)
        for ((number, type) in expected) {
            val info = readJson("""{"networkType":$number}""", MoneroDaemonInfo::class.java)
            assertEquals(type, info.networkType)
            assertEquals("""{"networkType":$number}""", write(info))
        }
    }

    // ------------------------------- common models ---------------------------------

    @Test
    fun testRpcPaymentInfoIsReadAndWritten() {
        val json = """{"credits":42,"topBlockHash":"${"a".repeat(64)}"}"""
        val info = readJson(json, MoneroRpcPaymentInfo::class.java)
        assertEquals(42L, info.credits)
        assertEquals("a".repeat(64), info.topBlockHash)
        assertSameJson(json, write(info))
    }

    @Test
    fun testRpcConnectionIsReadAndWritten() {
        val json = """{"uri":"http://127.0.0.1:18081","username":"user","password":"pass","proxyUri":"127.0.0.1:9050","zmqUri":"tcp://127.0.0.1:18083",
            "priority":2,"timeoutMs":5000,"sslVerify":false}"""
        val connection = readJson(json, MoneroRpcConnection::class.java)
        assertEquals("http://127.0.0.1:18081", connection.uri)
        assertEquals("user", connection.username)
        assertEquals("pass", connection.password)
        assertEquals("127.0.0.1:9050", connection.proxyUri)
        assertEquals("tcp://127.0.0.1:18083", connection.zmqUri)
        assertEquals(2, connection.priority)
        assertEquals(5000L, connection.timeoutMs)
        assertEquals(false, connection.sslVerify)
        assertSameJson(json, write(connection))
    }

    @Test
    fun testRpcConnectionPriorityAndTimeoutAreReadAndWritten() {
        val connection = readJson("""{"uri":"http://127.0.0.1:18081","priority":2,"timeoutMs":5000}""", MoneroRpcConnection::class.java)
        assertEquals(2, connection.priority)
        assertEquals(5000L, connection.timeoutMs)
        assertTrue(""""priority":2""" in write(connection) && """"timeoutMs":5000""" in write(connection))
    }

    // monero-python drops them when the mining is not active, as monero-cpp reads the status. Here the JSON is what the model has
    @Test
    fun testMiningStatusWhenNotActiveKeepsWhatTheJsonHas() {
        val json = """{"isActive":false,"isBackground":true,"address":"4abc","speed":500,"numThreads":4}"""
        val status = readJson(json, MoneroMiningStatus::class.java)
        assertEquals(false, status.isActive)
        assertEquals(true, status.isBackground)
        assertEquals("4abc", status.address)
        assertSameJson(json, write(status))
    }

    @Test
    fun testMinerDataDifficultyIsTheHexadecimalTextOfTheDaemon() {
        val json = """{"difficulty":"0x1f4"}"""
        assertEquals("0x1f4", readJson(json, MoneroMinerData::class.java).difficulty)
        assertEquals(json, write(readJson(json, MoneroMinerData::class.java)))
    }

    @Test
    fun testAuxiliaryPowOfTheArgumentsHasThem() {
        val pow = MoneroAuxiliaryPow("a".repeat(64), "b".repeat(64))
        assertEquals("a".repeat(64), pow.id)
        assertEquals("b".repeat(64), pow.hash)
        assertSameJson("""{"id":"${"a".repeat(64)}","hash":"${"b".repeat(64)}"}""", write(pow))
    }
}
