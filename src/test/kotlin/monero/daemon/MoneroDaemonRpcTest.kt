package monero.daemon

import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigInteger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.NotImplemented
import monero.RegtestSupport
import monero.TestLog
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.common.MoneroRpcConnection
import monero.common.MoneroRpcError
import monero.daemon.model.MoneroAuxiliaryPow
import monero.daemon.model.MoneroBan
import monero.daemon.model.MoneroBlockHeader
import monero.daemon.model.MoneroDaemonListener
import monero.daemon.model.MoneroKeyImageSpentStatus
import monero.daemon.model.MoneroOutput
import monero.daemon.model.MoneroTx
import monero.wallet.TxAssertions
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroTxConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.abort
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag

/**
 * Daemon RPC integration tests, like the tests of monero-python (tests/test_monero_daemon_rpc.py). They need monero-c and a regtest node,
 * which integration-tests.yml starts with the docker-compose.yml of src/test/docker. RegtestSupport says how to point them at another
 * node. The chain and the wallet that they need are made by [DaemonTestChain], which the setup of monero-python does for its tests. The two
 * tests that start the miner of the daemon are tagged mining, since the node then holds 2 GB for the RandomX dataset: a machine that has not
 * that memory leaves them out with --exclude-tag mining.
 */
@Tag("integration")
class MoneroDaemonRpcTest {

    companion object {

        private val logger = TestLog.logger("MoneroDaemonRpcTest")

        // the context of the blocks that come in a binary request
        private val BINARY_BLOCK_CTX = BinaryBlockContext()

        // the server that python gives a daemon which must not reach a node
        private const val OFFLINE_SERVER_URI = "offline_server_uri"

        // the daemon of the tests, which the class shares as the fixture of monero-python does
        private var shared: MoneroDaemonRpc? = null

        @JvmStatic
        @BeforeAll
        fun prepareTheChain() {
            RegtestSupport.assumeAvailable()
            DaemonTestChain.prepare()
            shared = MoneroDaemonRpc(RegtestSupport.daemonConnection())
        }

        @JvmStatic
        @AfterAll
        fun closeTheDaemon() {
            shared?.close()
            shared = null
        }
    }

    private val daemon: MoneroDaemonRpc get() = checkNotNull(shared) { "The daemon of the tests is not there" }

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    // the models are compared by what they say, as AssertUtils.assert_equals of monero-python does
    private fun assertSerializedEquals(expected: Any?, actual: Any?) {
        assertEquals(MoneroJson.serialize(expected), MoneroJson.serialize(actual))
    }

    // a test of monero-python that is marked xfail for an error of the daemon: it is skipped when the daemon answers with the error, and a pass
    // when it does not
    private fun xfailRpcError(reason: String, test: () -> Unit) {
        try {
            test()
        } catch (e: MoneroRpcError) {
            abort("$reason: $e")
        }
    }

    // two connections are the same when they have what monero-python compares: the address, the login, the proxy, the priority and the timeout. They
    // are compared as monero-c writes them, since it gives the connection its defaults, such as the priority 0, which a connection that was made
    // with an address has not read yet, and not what the last check of each found out
    private fun assertSameConnection(expected: MoneroRpcConnection, actual: MoneroRpcConnection) {
        fun fields(connection: MoneroRpcConnection) = (MoneroJson.mapper.readTree(connection.serialize()) as ObjectNode).also {
            it.remove(listOf("isOnline", "isAuthenticated", "responseTime"))
        }
        assertEquals(fields(expected), fields(actual))
    }

    private fun assertErrorMessage(expected: String, block: () -> Unit) {
        val error = assertFailsWith<Exception> { block() }
        assertEquals(expected, error.message)
    }

    //#region Non Relays Tests

    // Test offline daemon connection
    @Test
    fun testOfflineDaemon() {
        // create connection to offline uri
        MoneroRpcConnection(OFFLINE_SERVER_URI).use { offlineConnection ->
            assertTrue(offlineConnection.checkConnection())
            assertEquals(false, offlineConnection.isConnected())
            assertEquals(false, offlineConnection.isOnline)

            // create daemon
            MoneroDaemonRpc(offlineConnection).use { offline ->
                // test daemon connection
                assertSameConnection(offlineConnection, offline.getRpcConnection())
                assertFalse(offline.isConnected())

                // call to any daemon method should throw network error
                val error = assertFailsWith<Exception> { offline.getHeight() }
                assertTrue(error.message!!.startsWith("Network error: no response from $OFFLINE_SERVER_URI"), error.message)
            }
        }
    }

    // Can get the daemon's version
    @Test
    fun testGetVersion() {
        val version = daemon.getVersion()
        logger.fine("Testing monero version: ${MoneroJson.serialize(version)}")
        assertTrue(assertNotNull(version.number) > 0)
        assertNotNull(version.isRelease)
    }

    // Can indicate if it's trusted
    @Test
    fun testIsTrusted() {
        logger.fine("Trusted daemon: ${daemon.isTrusted()}")
    }

    // Can get the blockchain height
    @Test
    fun testGetHeight() {
        val height = daemon.getHeight()
        assertTrue(height > 0, "Height must be greater than 0")
    }

    // Can get a block hash by height
    @Test
    fun testGetBlockIdByHeight() {
        val lastHeader = daemon.getLastBlockHeader()
        val hash = daemon.getBlockHash(assertNotNull(lastHeader.height))
        assertEquals(64, hash.length, "Invalid block hash '$hash'")
    }

    // Can get a block template
    @Test
    fun testGetBlockTemplate() {
        val template = daemon.getBlockTemplate(RegtestSupport.MINING_ADDRESS, 2)
        BlockAssertions.testBlockTemplate(template)
    }

    // Can get the last block's header
    @Test
    fun testGetLastBlockHeader() {
        BlockAssertions.testBlockHeader(daemon.getLastBlockHeader(), true)
    }

    // Can get a block header by hash
    @Test
    fun testGetBlockHeaderByHash() {
        // retrieve last block by hash
        val lastHeader = daemon.getLastBlockHeader()
        val lastHeight = assertNotNull(lastHeader.height)
        var hash = daemon.getBlockHash(lastHeight)
        var header = daemon.getBlockHeaderByHash(hash)
        BlockAssertions.testBlockHeader(header, true)
        assertSerializedEquals(lastHeader, header)

        // retrieve by hash of previous to last block
        hash = daemon.getBlockHash(lastHeight - 1)
        header = daemon.getBlockHeaderByHash(hash)
        BlockAssertions.testBlockHeader(header, true)
        assertEquals(lastHeight - 1, header.height)
    }

    // Can get a block header by height
    @Test
    fun testGetBlockHeaderByHeight() {
        // retrieve by height of last block
        val lastHeader = daemon.getLastBlockHeader()
        val lastHeight = assertNotNull(lastHeader.height)
        var header = daemon.getBlockHeaderByHeight(lastHeight)
        BlockAssertions.testBlockHeader(header, true)
        assertSerializedEquals(lastHeader, header)

        // retrieve by height of previous to last block
        header = daemon.getBlockHeaderByHeight(lastHeight - 1)
        BlockAssertions.testBlockHeader(header, true)
        assertEquals(lastHeight - 1, header.height)
    }

    // Can get block headers by range
    // TODO: test start with no end, vice versa, inclusivity
    @Test
    fun testGetBlockHeadersByRange() {
        // determine start and end height based on number of blocks and how many blocks ago
        val numBlocks = 100
        val numBlocksAgo = 100
        val currentHeight = daemon.getHeight()
        val startHeight = currentHeight - numBlocksAgo
        val endHeight = currentHeight - (numBlocksAgo - numBlocks) - 1

        // fetch headers
        val headers = daemon.getBlockHeadersByRange(startHeight, endHeight)

        // test headers
        assertEquals(numBlocks, headers.size)
        for (i in 0 until numBlocks) {
            val header = headers[i]
            assertEquals(startHeight + i, header.height)
            BlockAssertions.testBlockHeader(header, true)
        }
    }

    // Can get a block by hash
    @Test
    fun testGetBlockByHash() {
        // the last blocks have no txs, which a block that is mined takes from the pool
        RegtestSupport.mineBlocks(3)

        // test config
        val ctx = TestContext()
        ctx.hasHex = true
        ctx.hasTxs = false
        ctx.headerIsFull = true

        // retrieve by hash of last block
        val lastHeader = daemon.getLastBlockHeader()
        val lastHeight = assertNotNull(lastHeader.height)
        var hash = daemon.getBlockHash(lastHeight)
        var block = daemon.getBlockByHash(hash)
        BlockAssertions.testBlock(block, ctx)
        assertSerializedEquals(daemon.getBlockByHeight(assertNotNull(block.height)), block)
        assertEquals(0, block.txs.orEmpty().size, "No block tx expected, found: ${block.txs.orEmpty().size}")

        // retrieve by hash of previous to last block
        hash = daemon.getBlockHash(lastHeight - 1)
        block = daemon.getBlockByHash(hash)
        BlockAssertions.testBlock(block, ctx)
        assertSerializedEquals(daemon.getBlockByHeight(lastHeight - 1), block)
        assertEquals(0, block.txs.orEmpty().size, "No block tx expected, found: ${block.txs.orEmpty().size}")
    }

    // Can get blocks by hash which includes transactions (binary)
    @Test
    fun testGetBlocksByHashBinary() {
        val chainHeight = daemon.getHeight()
        val genesisHash = daemon.getBlockHash(0)

        // a non-zero start height resumes from there and ignores the hash list
        val resumeHeight = chainHeight - 5
        var result = daemon.getBlocksByHash(listOf(genesisHash), resumeHeight, false, 0)
        assertTrue(assertNotNull(result.currentHeight) >= chainHeight)
        val blocks = result.blocks.orEmpty()
        assertTrue(blocks.isNotEmpty())
        for ((i, block) in blocks.withIndex()) {
            assertEquals(resumeHeight + i, block.height)
            BlockAssertions.testBlock(block, BINARY_BLOCK_CTX)
        }

        // a genesis-terminated history resumes from height 0, capped by max_block_count
        result = daemon.getBlocksByHash(listOf(genesisHash), 0, false, 10)
        assertTrue(result.blocks.orEmpty().size in 1..10)
        assertEquals(0L, result.blocks!![0].height)

        // noop when the first hash is already the tip
        val tip = daemon.getLastBlockHeader()
        result = daemon.getBlocksByHash(listOf(assertNotNull(tip.hash), genesisHash), 0, false, 0)
        assertEquals(0, result.blocks.orEmpty().size)
    }

    // Can get a block by height
    @Test
    fun testGetBlockByHeight() {
        // the last blocks have no txs, which a block that is mined takes from the pool
        RegtestSupport.mineBlocks(3)

        // config for testing blocks
        val ctx = TestContext()
        ctx.hasHex = true
        ctx.headerIsFull = true
        ctx.hasTxs = false

        // retrieve by height of last block
        val lastHeader = daemon.getLastBlockHeader()
        val lastHeight = assertNotNull(lastHeader.height)
        var block = daemon.getBlockByHeight(lastHeight)
        BlockAssertions.testBlock(block, ctx)
        assertSerializedEquals(daemon.getBlockByHeight(assertNotNull(block.height)), block)

        // retrieve by height of previous to last block
        block = daemon.getBlockByHeight(lastHeight - 1)
        BlockAssertions.testBlock(block, ctx)
        assertEquals(lastHeight - 1, block.height)
    }

    // Can get blocks by height which includes transactions (binary)
    @Test
    fun testGetBlocksByHeightBinary() {
        // set number of blocks to test
        val numBlocks = 100

        // select the last heights
        val currentHeight = daemon.getHeight()
        val numHeights = currentHeight - 1
        val startHeight = numHeights - numBlocks
        val heights = (startHeight until numHeights).toList()

        // fetch blocks
        val blocks = daemon.getBlocksByHeight(heights)

        // test blocks
        var txFound = false
        assertEquals(numBlocks, blocks.size)

        for ((i, height) in heights.withIndex()) {
            val block = blocks[i]
            if (block.txs.orEmpty().isNotEmpty()) txFound = true
            BlockAssertions.testBlock(block, BINARY_BLOCK_CTX)
            assertEquals(height, block.height)
        }

        assertTrue(txFound, "No transactions found to test")
    }

    // Can get blocks by range in a single request
    @Test
    fun testGetBlocksByRange() {
        // get height range
        val numBlocks = 100L
        val numBlocksAgo = 102L
        assertTrue(numBlocks > 0)
        assertTrue(numBlocksAgo >= numBlocks)
        val height = daemon.getHeight()
        assertTrue(height - numBlocksAgo + numBlocks - 1 < height)
        val startHeight = height - numBlocksAgo
        val endHeight = height - numBlocksAgo + numBlocks - 1

        // test known start and end heights
        BlockAssertions.testGetBlocksRange(daemon, startHeight, endHeight, height, false, BINARY_BLOCK_CTX)

        // test unspecified start
        BlockAssertions.testGetBlocksRange(daemon, null, numBlocks - 1, height, false, BINARY_BLOCK_CTX)

        // test unspecified end
        BlockAssertions.testGetBlocksRange(daemon, height - numBlocks - 1, null, height, false, BINARY_BLOCK_CTX)
    }

    // Test invalid blocks range
    @Test
    fun testGetBlocksByRangeInvalid() {
        val height = daemon.getHeight()
        val blocks = daemon.getBlocksByRangeChunked(height, height - 10)
        assertEquals(0, blocks.size, "Expected empty blocks for invalid range request, got ${blocks.size}")
    }

    // Can get blocks by range using chunked requests
    @Test
    fun testGetBlocksByRangeChunked() {
        // get long height range
        val numBlocks = minOf(daemon.getHeight() - 2, 1440L) // test up to ~2 days of blocks
        assertTrue(numBlocks > 0)
        val height = daemon.getHeight()
        assertTrue(height - numBlocks - 1 < height)
        val startHeight = height - numBlocks
        val endHeight = height - 1

        // test known start and end heights
        BlockAssertions.testGetBlocksRange(daemon, startHeight, endHeight, height, true, BINARY_BLOCK_CTX)

        // test unspecified start
        BlockAssertions.testGetBlocksRange(daemon, null, numBlocks - 1, height, true, BINARY_BLOCK_CTX)

        // test unspecified end
        BlockAssertions.testGetBlocksRange(daemon, endHeight - numBlocks - 1, null, height, true, BINARY_BLOCK_CTX)
    }

    // Can get genesis block by range using chunked requests
    @Test
    fun testGetGenesisBlockByRangeChunked() {
        // get current chain height
        val chainHeight = daemon.getHeight()

        // test genesis
        BlockAssertions.testGetBlocksRange(daemon, 0, 0, chainHeight, true, BINARY_BLOCK_CTX)
    }

    // Can get block hashes (binary)
    @Test
    fun testGetBlockHashesBinary() {
        val chainHeight = daemon.getHeight()
        val genesisHash = daemon.getBlockHash(0)

        // a genesis-terminated history returns the whole chain from height 0
        var result = daemon.getBlockHashes(listOf(genesisHash))
        assertEquals(0L, result.startHeight)
        assertTrue(assertNotNull(result.currentHeight) >= chainHeight)
        val hashes = result.hashes.orEmpty()
        assertTrue(hashes.isNotEmpty())
        assertEquals(genesisHash, hashes[0])
        for (hash in hashes) assertEquals(64, hash.length)

        // only the tip comes back when it is already the first hash
        val tip = daemon.getLastBlockHeader()
        val tipHash = assertNotNull(tip.hash)
        result = daemon.getBlockHashes(listOf(tipHash, genesisHash))
        assertEquals(listOf(tipHash), result.hashes)
    }

    // Can get a transaction by hash and without pruning
    @Test
    fun testGetTxByHash() {
        // fetch tx hashses to test
        val txHashes = DaemonAssertions.getConfirmedTxHashes(daemon)

        // context for creating txs
        val ctx = TestContext()
        ctx.isPruned = false
        ctx.isConfirmed = true
        ctx.fromGetTxPool = false

        // fetch each tx by hash without pruning
        for (txHash in txHashes) DaemonTxAssertions.testTx(daemon.getTx(txHash), ctx)

        // fetch each tx by hash with pruning
        for (txHash in txHashes) {
            ctx.isPruned = true
            DaemonTxAssertions.testTx(daemon.getTx(txHash, true), ctx)
        }

        // fetch invalid hash
        assertErrorMessage("Invalid transaction hash") { daemon.getTx("invalid tx hash") }
    }

    // Can get transactions by hashes with and without pruning
    @Test
    fun testGetTxsByHashes() {
        // fetch tx hashses to test
        val txHashes = DaemonAssertions.getConfirmedTxHashes(daemon)
        assertTrue(txHashes.isNotEmpty(), "No tx hashes found")

        // context for creating txs
        val ctx = TestContext()
        ctx.isPruned = false
        ctx.isConfirmed = true
        ctx.fromGetTxPool = false

        // fetch each tx by hash without pruning
        var txs = daemon.getTxs(txHashes)
        assertEquals(txHashes.size, txs.size, "Expected len(txs) == len(tx_hashes), got ${txs.size} == ${txHashes.size}")
        for (tx in txs) DaemonTxAssertions.testTx(tx, ctx)

        // fetch each tx by hash with pruning
        txs = daemon.getTxs(txHashes, true)
        ctx.isPruned = true
        assertEquals(txHashes.size, txs.size, "Expected len(txs) == len(tx_hashes), got ${txs.size} == ${txHashes.size}")
        for (tx in txs) DaemonTxAssertions.testTx(tx, ctx)

        // fetch missing hash
        val dest = MoneroDestination()
        dest.address = DaemonTestChain.wallet().getPrimaryAddress()
        dest.amount = TxAssertions.MAX_FEE
        val config = MoneroTxConfig()
        config.accountIndex = 0
        config.destinations = listOf(dest)
        val tx = DaemonTestChain.wallet().createTx(config)
        val txHash = assertNotNull(tx.hash)
        assertNull(daemon.getTx(txHash))
        txHashes.add(txHash)
        val numTxs = txs.size
        txs = daemon.getTxs(txHashes)
        assertEquals(numTxs, txs.size)

        // fetch invalid hash
        assertErrorMessage("Invalid transaction hash") { daemon.getTxs(listOf("invalid tx hash")) }
    }

    // Can get transactions by hashes that are in the transaction pool
    @Test
    fun testGetTxsByHashesInPool() {
        // wait for wallet's txs in the pool to clear to ensure reliable sync
        DaemonTestChain.waitForTxsToClearPool(daemon)

        // submit txs to the pool but don't relay
        val txHashes = ArrayList<String>()
        for (i in 1..2) {
            val tx = DaemonTestChain.getUnrelayedTx(i)
            val fullHex = assertNotNull(tx.fullHex)
            assertTrue(fullHex.isNotEmpty())
            val result = daemon.submitTxHex(fullHex, true)
            DaemonAssertions.testSubmitTxResultGood(result)
            assertEquals(false, result.isRelayed)
            txHashes.add(assertNotNull(tx.hash))
        }

        // fetch txs by hash
        logger.fine("Fetching txs...")
        val txs = daemon.getTxs(txHashes)
        val numTxs = txs.size
        logger.fine("Fetched $numTxs tx(s)")

        // context for testing tx
        val ctx = TestContext()
        ctx.isPruned = false
        ctx.isConfirmed = false
        ctx.fromGetTxPool = false

        // test fetched txs
        assertEquals(txHashes.size, numTxs)
        for (tx in txs) DaemonTxAssertions.testTx(tx, ctx)

        // clear txs from pool
        daemon.flushTxPool(txHashes)
        DaemonTestChain.wallet().sync()
    }

    // Can get a transaction hex by hash with and without pruning
    @Test
    fun testGetTxHexByHash() {
        // fetch transaction hashes to test
        val txHashes = DaemonAssertions.getConfirmedTxHashes(daemon)

        // fetch each tx hex by hash with and without pruning
        val hexes = ArrayList<String>()
        val hexesPruned = ArrayList<String>()
        for (txHash in txHashes) {
            hexes.add(daemon.getTxHex(txHash))
            hexesPruned.add(daemon.getTxHex(txHash, true))
        }

        // test results
        assertEquals(txHashes.size, hexes.size)
        assertEquals(txHashes.size, hexesPruned.size)

        for ((i, txHex) in hexes.withIndex()) {
            val prunedTxHex = hexesPruned[i]
            assertTrue(prunedTxHex.isNotEmpty())
            // pruned hex is shorter
            assertTrue(txHex.length >= prunedTxHex.length)
        }

        // fetch invalid hash
        assertErrorMessage("Invalid transaction hash") { daemon.getTxHex("invalid tx hash") }
    }

    // Can get transaction hexes by hashes with and without pruning
    @Test
    fun testGetTxHexesByHashes() {
        // fetch transaction hashes to test
        val txHashes = DaemonAssertions.getConfirmedTxHashes(daemon)

        // fetch tx hexes by hash with and without pruning
        val hexes = daemon.getTxHexes(txHashes)
        val hexesPruned = daemon.getTxHexes(txHashes, true)

        // test results
        assertEquals(txHashes.size, hexes.size)
        assertEquals(txHashes.size, hexesPruned.size)
        for ((i, txHex) in hexes.withIndex()) {
            val prunedTxHex = hexesPruned[i]
            assertTrue(prunedTxHex.isNotEmpty())
            // pruned hex is shorter
            assertTrue(txHex.length >= prunedTxHex.length)
        }

        // fetch invalid hash
        txHashes.add("invalid tx hash")
        assertErrorMessage("Invalid transaction hash") { daemon.getTxHexes(txHashes) }
    }

    // Can get the miner tx sum
    @Test
    fun testGetMinerTxSum() {
        val txSum = daemon.getMinerTxSum(0, minOf(5000L, daemon.getHeight()))
        DaemonAssertions.testMinerTxSum(txSum)
    }

    // Can get fee estimate
    @Test
    fun testGetFeeEstimate() {
        val feeEstimate = daemon.getFeeEstimate()
        logger.fine("Testing fee estimate: ${MoneroJson.serialize(feeEstimate)}")
        GenAssertions.testUnsignedBigInteger(feeEstimate.fee, true)
        assertEquals(4, feeEstimate.fees.orEmpty().size, "Exptected 4 fees")
        for (fee in feeEstimate.fees.orEmpty()) GenAssertions.testUnsignedBigInteger(fee, true)
        GenAssertions.testUnsignedBigInteger(feeEstimate.quantizationMask, true)
    }

    // Can get all transactions in the transaction pool
    @Test
    fun testGetTxsInPool() {
        DaemonTestChain.waitForTxsToClearPool(daemon)

        // submit tx to pool but don't relay
        val tx = DaemonTestChain.getUnrelayedTx(1)
        val result = daemon.submitTxHex(assertNotNull(tx.fullHex), true)
        DaemonAssertions.testSubmitTxResultGood(result)
        assertEquals(false, result.isRelayed)

        // fetch txs in pool
        val txs = daemon.getTxPool()
        val numTxs = txs.size

        // fetch tx hashes in pool
        val txHashes = daemon.getTxPoolHashes()
        val numTxHashes = txHashes.size

        // context for testing tx
        val ctx = TestContext()
        ctx.isPruned = false
        ctx.isConfirmed = false
        ctx.fromGetTxPool = true

        // test txs
        assertTrue(numTxs > 0, "Test requires an unconfirmed tx in the tx pool")
        assertEquals(numTxs, numTxHashes, "Txs in pool $numTxs != Txs hashes in pool $numTxHashes")
        for (aTx in txs) {
            DaemonTxAssertions.testTx(aTx, ctx)
            assertTrue(aTx.hash in txHashes)
        }

        // flush the tx from the pool, gg
        daemon.flushTxPool(assertNotNull(tx.hash))
        DaemonTestChain.wallet().sync()
    }

    // Can get transaction pool statistics
    @Test
    fun testGetTxPoolStatistics() {
        DaemonTestChain.waitForTxsToClearPool(daemon)
        val txIds = ArrayList<String>()
        try {
            // submit txs to the pool but don't relay
            for (i in 1..2) {
                // submit tx hex
                val tx = DaemonTestChain.getUnrelayedTx(i)
                val result = daemon.submitTxHex(assertNotNull(tx.fullHex), true)
                assertTrue(result.isGood == true, "Expected True, got ${result.isGood}")
                txIds.add(assertNotNull(tx.hash))

                // get tx pool stats
                val stats = daemon.getTxPoolStats()
                assertTrue(assertNotNull(stats.numTxs) > i - 1)
                DaemonAssertions.testTxPoolStats(stats)
            }
        } finally {
            // flush txs
            daemon.flushTxPool(txIds)
        }
    }

    // Can flush all transactions from the pool
    @Test
    fun testFlushTxsFromPool() {
        DaemonTestChain.waitForTxsToClearPool(daemon)

        // preserve original transactions in the pool
        val txPoolBefore = daemon.getTxPool()

        // submit txs to the pool but don't relay
        for (i in 1..2) {
            val tx = DaemonTestChain.getUnrelayedTx(i)
            val result = daemon.submitTxHex(assertNotNull(tx.fullHex), true)
            DaemonAssertions.testSubmitTxResultGood(result)
        }

        assertEquals(txPoolBefore.size + 2, daemon.getTxPool().size)

        // flush tx pool
        daemon.flushTxPool()
        assertEquals(0, daemon.getTxPool().size)

        // re-submit original transactions
        for (tx in txPoolBefore) {
            val isRelayed = assertNotNull(tx.isRelayed)
            daemon.submitTxHex(assertNotNull(tx.fullHex), isRelayed)
        }

        // pool is back to original state
        assertEquals(txPoolBefore.size, daemon.getTxPool().size)

        // sync wallet for next test
        DaemonTestChain.wallet().sync()
    }

    // Can flush a transaction from the pool by hash
    @Test
    fun testFlushTxFromPoolByHash() {
        DaemonTestChain.waitForTxsToClearPool(daemon)

        // preserve original transactions in the pool
        val txPoolBefore = daemon.getTxPool()

        // submit txs to the pool but don't relay
        val txs = ArrayList<MoneroTx>()
        for (i in 1..2) {
            val tx = DaemonTestChain.getUnrelayedTx(i)
            val result = daemon.submitTxHex(assertNotNull(tx.fullHex), true)
            DaemonAssertions.testSubmitTxResultGood(result)
            txs.add(tx)
        }

        // remove each tx from the pool by hash and test
        val numTxs = txs.size
        for ((i, tx) in txs.withIndex()) {
            // flush tx from pool
            daemon.flushTxPool(assertNotNull(tx.hash))

            // test tx pool
            val poolTxs = daemon.getTxPool()
            assertEquals(numTxs - i - 1, poolTxs.size - txPoolBefore.size)
        }

        // pool is back to original state
        assertEquals(txPoolBefore.size, daemon.getTxPool().size)

        // sync wallet for next test
        DaemonTestChain.wallet().sync()
    }

    // Can flush transactions from the pool by hashes
    @Test
    fun testFlushTxsFromPoolByHashes() {
        DaemonTestChain.waitForTxsToClearPool(daemon)

        // preserve original transactions in the pool
        val txPoolBefore = daemon.getTxPool()

        // submit txs to the pool but don't relay
        val txHashes = ArrayList<String>()
        for (i in 1..2) {
            val tx = DaemonTestChain.getUnrelayedTx(i)
            val result = daemon.submitTxHex(assertNotNull(tx.fullHex), true)
            DaemonAssertions.testSubmitTxResultGood(result)
            txHashes.add(assertNotNull(tx.hash))
        }

        assertEquals(txPoolBefore.size + txHashes.size, daemon.getTxPool().size)

        // remove all txs by hashes
        daemon.flushTxPool(txHashes)

        // pool is back to original state
        assertEquals(txPoolBefore.size, daemon.getTxPool().size)
        DaemonTestChain.wallet().sync()
    }

    @Test
    fun testGetSpentStatusOfKeyImages() {
        DaemonTestChain.waitForTxsToClearPool(daemon)

        // submit txs to the pool to collect key images then flush them
        val txs = ArrayList<MoneroTx>()
        for (i in 1..2) {
            val tx = DaemonTestChain.getUnrelayedTx(i)
            daemon.submitTxHex(assertNotNull(tx.fullHex), true)
            txs.add(tx)
        }

        var keyImages = ArrayList<String>()
        val txHashes = txs.map { assertNotNull(it.hash) }

        for (tx in daemon.getTxs(txHashes)) {
            for (input in tx.inputs.orEmpty()) keyImages.add(assertNotNull(assertNotNull(input.keyImage).hex))
        }

        daemon.flushTxPool(txHashes)

        // key images are not spent
        DaemonAssertions.testSpentStatuses(daemon, keyImages, MoneroKeyImageSpentStatus.NOT_SPENT)

        // submit txs to the pool but don't relay
        for (tx in txs) daemon.submitTxHex(assertNotNull(tx.fullHex), true)

        // key images are in the tx pool
        DaemonAssertions.testSpentStatuses(daemon, keyImages, MoneroKeyImageSpentStatus.TX_POOL)

        // collect key images of confirmed txs
        keyImages = ArrayList()
        for (tx in DaemonAssertions.getConfirmedTxs(daemon, 10)) {
            for (input in tx.inputs.orEmpty()) keyImages.add(assertNotNull(assertNotNull(input.keyImage).hex))
        }

        // key images are all spent
        DaemonAssertions.testSpentStatuses(daemon, keyImages, MoneroKeyImageSpentStatus.CONFIRMED)

        // flush this test's txs from pool
        daemon.flushTxPool(txHashes)
    }

    // Can get an output histogram (binary)
    @Test
    fun testGetOutputHistogramBinary() {
        val entries = daemon.getOutputHistogram(emptyList(), null, null, null, null)
        assertTrue(entries.isNotEmpty())
        for (entry in entries) OutputAssertions.testOutputHistogramEntry(entry)
    }

    // Can get an output distribution
    @Test
    fun testGetOutputDistribution() {
        val amounts = listOf(BigInteger.ZERO)
        val entries = daemon.getOutputDistribution(amounts)
        for (entry in entries) OutputAssertions.testOutputDistributionEntry(entry)
    }

    // Can get general information
    @Test
    fun testGetGeneralInformation() {
        val info = daemon.getInfo()
        DaemonAssertions.testInfo(info)
        assertEquals(true, info.isRegtest)
    }

    // Can get sync information
    @Test
    fun testGetSyncInformation() {
        DaemonAssertions.testSyncInfo(daemon.getSyncInfo())
    }

    // Can get hard fork information
    @Test
    fun testGetHardForkInformation() {
        DaemonAssertions.testHardForkInfo(daemon.getHardForkInfo())
    }

    // Can get alternative chains
    @Test
    fun testGetAlternativeChains() {
        val altChains = daemon.getAltChains()
        if (altChains.isEmpty()) logger.warning("No alternative chain found!")
        for (altChain in altChains) BlockAssertions.testAltChain(altChain)
    }

    // Can get alternative block hashes
    @Test
    fun testGetAlternativeBlockIds() {
        val altBlockIds = daemon.getAltBlockHashes()
        if (altBlockIds.isEmpty()) logger.warning("No alternative block ids found!")
        for (altBlockId in altBlockIds) {
            // TODO: common validation
            assertEquals(64, altBlockId.length)
        }
    }

    // Can get, set, and reset a download bandwidth limit
    @Test
    fun testSetDownloadBandwidth() {
        val initVal = daemon.getDownloadLimit()
        assertTrue(initVal > 0)
        val setVal = initVal * 2
        daemon.setDownloadLimit(setVal)
        assertEquals(setVal, daemon.getDownloadLimit())
        val resetVal = daemon.resetDownloadLimit()
        assertEquals(initVal, resetVal)

        // test invalid limits
        assertErrorMessage("Download limit must be an integer greater than 0") { daemon.setDownloadLimit(0) }

        assertEquals(initVal, daemon.getDownloadLimit())
    }

    // Can get, set, and reset an upload bandwidth limit
    @Test
    fun testSetUploadBandwidth() {
        val initVal = daemon.getUploadLimit()
        assertTrue(initVal > 0)
        val setVal = initVal * 2
        daemon.setUploadLimit(setVal)
        assertEquals(setVal, daemon.getUploadLimit())
        val resetVal = daemon.resetUploadLimit()
        assertEquals(initVal, resetVal)

        // test invalid limits
        assertErrorMessage("Upload limit must be an integer greater than 0") { daemon.setUploadLimit(0) }

        assertEquals(initVal, daemon.getUploadLimit())
    }

    // Can get peers with active incoming or outgoing connections
    @Test
    fun testGetPeers() {
        val peers = daemon.getPeers()
        val seen = HashSet<Pair<String, Int>>()
        for (peer in peers) {
            val peerKey = assertNotNull(peer.host) to assertNotNull(peer.port)
            assertTrue(seen.add(peerKey), "Duplicate peer found: $peerKey")
            assertEquals(true, peer.isOnline, "Expected active peer connection to be online")
            DaemonAssertions.testPeer(peer)
        }
    }

    // Can get all known peers which may be online or offline
    @Test
    fun testGetKnownPeers() {
        val peers = daemon.getKnownPeers()
        assertEquals(0, peers.size, "Regtest daemon should not have known peers to test")
        logger.warning("Regtest daemon doesn't have known peers to test")
        for (peer in peers) DaemonAssertions.testKnownPeer(peer, false)
    }

    // Can limit the number of outgoing peers
    @Test
    fun testSetOutgoingPeerLimit() {
        daemon.setOutgoingPeerLimit(0)
        daemon.setOutgoingPeerLimit(8)
        daemon.setOutgoingPeerLimit(10)
    }

    // Can limit the number of incoming peers
    @Test
    fun testSetIncomingPeerLimit() {
        daemon.setIncomingPeerLimit(0)
        daemon.setIncomingPeerLimit(8)
        daemon.setIncomingPeerLimit(10)
    }

    // Can notify listeners when a new block is added to the chain
    @Test
    fun testBlockListener() {
        // register a listener
        var lastHeader: MoneroBlockHeader? = null
        val listener = object : MoneroDaemonListener() {
            override fun onBlockHeader(header: MoneroBlockHeader) {
                lastHeader = header
            }
        }
        daemon.addListener(listener)
        try {
            // push the chain along: regtest mines a block when asked, where monero-python starts the miner of the daemon
            val header = CompletableFuture<MoneroBlockHeader?>()
            Thread {
                try {
                    header.complete(daemon.waitForNextBlockHeader(30000))
                } catch (e: Throwable) {
                    header.completeExceptionally(e)
                }
            }.apply { isDaemon = true; start() }
            Thread.sleep(500)
            RegtestSupport.mineBlocks(1)

            // wait for next block notification
            val arrived = assertNotNull(header.get(35, TimeUnit.SECONDS), "no block arrived")
            daemon.removeListener(listener) // unregister listener so daemon does not keep polling
            BlockAssertions.testBlockHeader(arrived, true)

            // test that listener was called with equivalent header
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (lastHeader == null && System.nanoTime() < deadline) Thread.sleep(50)
            assertSerializedEquals(arrived, lastHeader)
        } finally {
            daemon.removeListener(listener)
        }
    }

    // Can ban a peer
    @Test
    fun testBanPeer() {
        // set ban
        val host = "192.168.1.51"
        val ban = MoneroBan()
        ban.host = host
        ban.isBanned = true
        ban.seconds = 60
        daemon.setPeerBan(ban)

        // test ban
        val bans = daemon.getPeerBans()
        var found = false
        for (peerBan in bans) {
            DaemonAssertions.testBan(peerBan)
            if (host == peerBan.host) found = true
        }

        assertTrue(found, "Could not find peer ban $host")
    }

    // Can ban peers
    @Test
    fun testBanPeers() {
        // set bans
        val addr1 = "192.168.1.52"
        val addr2 = "192.168.1.53"
        val ban1 = MoneroBan()
        ban1.host = addr1
        ban1.isBanned = true
        ban1.seconds = 60
        val ban2 = MoneroBan()
        ban2.host = addr2
        ban2.isBanned = true
        ban2.seconds = 60
        daemon.setPeerBans(listOf(ban1, ban2))

        // test bans
        var found1 = false
        var found2 = false
        for (aBan in daemon.getPeerBans()) {
            DaemonAssertions.testBan(aBan)
            if (addr1 == aBan.host) found1 = true
            if (addr2 == aBan.host) found2 = true
        }

        assertTrue(found1, "Could not find peer ban1 $addr1")
        assertTrue(found2, "Could not find peer ban2 $addr2")
    }

    // Can start and stop mining
    @Test
    @Tag("mining")
    fun testMining() {
        // stop mining at beginning of test
        runCatching { daemon.stopMining() }

        // generate address to mine to
        val address = DaemonTestChain.wallet().getPrimaryAddress()

        // start mining
        daemon.startMining(address, 1, false, true)

        // stop mining
        daemon.stopMining()
    }

    // Can get mining status
    @Test
    @Tag("mining")
    fun testGetMiningStatus() {
        try {
            // stop mining at beginning of test
            runCatching { daemon.stopMining() }

            // test status without mining
            var status = daemon.getMiningStatus()
            logger.fine("Testing mining status: ${MoneroJson.serialize(status)}")
            assertEquals(false, status.isActive)
            assertNull(status.address, "Mining address is not None: ${status.address}")
            assertEquals(0L, status.speed)
            assertEquals(0, status.numThreads)
            assertNull(status.isBackground)

            // test status with mining
            val address = DaemonTestChain.wallet().getPrimaryAddress()
            val threadCount = 1
            val isBackground = false
            daemon.startMining(address, threadCount.toLong(), isBackground, true)
            status = daemon.getMiningStatus()
            logger.fine("Testing mining status: ${MoneroJson.serialize(status)}")
            assertTrue(assertNotNull(status.speed) >= 0)
            assertEquals(true, status.isActive)
            assertEquals(address, status.address)
            assertEquals(threadCount, status.numThreads)
            assertEquals(isBackground, status.isBackground)
        } finally {
            // stop mining at end of test
            runCatching { daemon.stopMining() }
        }
    }

    // Can submit a mined block to the network
    @Test
    fun testSubmitMinedBlock() {
        // get template to mine on
        val template = daemon.getBlockTemplate(RegtestSupport.MINING_ADDRESS)
        val blob = assertNotNull(template.blockTemplateBlob)

        // TODO monero rpc: way to get mining nonce when found in order to submit?

        // try to submit a block that is not valid. monero-python submits the template without a nonce, which its node, with a difficulty of 500,
        // does not take, but the difficulty of this node is 1 and any nonce does, so the block has the timestamp 0, which no node takes
        assertErrorMessage("Block not accepted") { daemon.submitBlock(withTimestampZero(blob)) }
    }

    // the blob of a block with its timestamp set to 0: the blob starts with the major version, the minor version and the timestamp, which are varints
    private fun withTimestampZero(blob: String): String {
        val bytes = blob.chunked(2).map { it.toInt(16) }
        fun endOfVarint(start: Int): Int {
            var i = start
            while ((bytes[i++] and 0x80) != 0) { /* the bytes of the number */ }
            return i
        }
        val timestampStart = endOfVarint(endOfVarint(0))
        val timestampEnd = endOfVarint(timestampStart)
        return blob.substring(0, timestampStart * 2) + "00" + blob.substring(timestampEnd * 2)
    }

    // Can prune the blockchain
    @Test
    fun testPruneBlockchain() {
        val result = daemon.pruneBlockchain(true)
        logger.fine("Testing prune result: ${MoneroJson.serialize(result)}")

        if (result.isPruned == true) {
            assertTrue(assertNotNull(result.pruningSeed) > 0)
        } else {
            assertEquals(0, result.pruningSeed)
        }
    }

    // Can check for an update
    @Test
    fun testCheckForUpdate() {
        xfailRpcError("the update check needs DNSSEC validated TXT records, which the daemon in docker does not get") {
            DaemonAssertions.testUpdateCheckResult(daemon.checkForUpdate())
        }
    }

    // Can download an update
    @Test
    fun testDownloadUpdate() {
        xfailRpcError("the update check needs DNSSEC validated TXT records, which the daemon in docker does not get") {
            // download to default path
            var result = daemon.downloadUpdate()
            DaemonAssertions.testUpdateDownloadResult(result)

            // download to defined path
            val path = "test_download_" + System.currentTimeMillis() + ".tar.bz2"
            result = daemon.downloadUpdate(path)
            DaemonAssertions.testUpdateDownloadResult(result)

            // test invalid path
            // TODO monerod: an invalid path causes a 500 in daemon rpc rather than a clean error
            if (result.isUpdateAvailable == true) {
                try {
                    daemon.downloadUpdate("./ohhai/there")
                } catch (e: Exception) {
                    logger.warning(e.toString())
                }
            }
        }
    }

    // Can be stopped
    @Test
    @Disabled("test is disabled to not interfere with other tests")
    fun testStop() {
        // stop the daemon
        daemon.stop()

        // give the daemon time to shut down
        Thread.sleep(5000)

        // try to interact with the stopped daemon
        assertFailsWith<Exception> { daemon.getHeight() }
    }

    // Can get the data needed to mine a block
    @Test
    fun testGetMinerData() {
        DaemonAssertions.testMinerData(daemon.getMinerData())
    }

    // Can calculate a block's proof-of-work hash
    @Test
    fun testCalculatePow() {
        val data = daemon.getMinerData()
        val template = daemon.getBlockTemplate(RegtestSupport.MINING_ADDRESS)
        val majorVersion = assertNotNull(data.majorVersion)
        val height = assertNotNull(data.height)
        val seedHash = assertNotNull(data.seedHash)
        val blob = assertNotNull(template.blockTemplateBlob)

        val powHash = daemon.calculatePow(majorVersion, height, blob, seedHash)
        assertEquals(64, powHash.length)

        assertErrorMessage("Must provide a block blob to hash") { daemon.calculatePow(majorVersion, height, "", seedHash) }
    }

    // Can add auxiliary proof-of-work to a block template
    @Test
    fun testAddAuxiliaryPow() {
        val template = daemon.getBlockTemplate(RegtestSupport.MINING_ADDRESS)
        val blob = assertNotNull(template.blockTemplateBlob)

        val auxPow = MoneroAuxiliaryPow("11".repeat(32), "22".repeat(32))
        val result = daemon.addAuxiliaryPow(blob, listOf(auxPow))
        assertTrue(assertNotNull(result.blockTemplateBlob).isNotEmpty())
        assertTrue(assertNotNull(result.blockHashingBlob).isNotEmpty())
        assertEquals(64, assertNotNull(result.merkleRoot).length)
        assertTrue(assertNotNull(result.merkleTreeDepth) >= 0)
        assertEquals(1, result.auxPow.orEmpty().size)
        assertEquals(64, assertNotNull(result.auxPow!![0].id).length)
        assertEquals(64, assertNotNull(result.auxPow!![0].hash).length)

        assertErrorMessage("Must provide auxiliary proof of work to add") { daemon.addAuxiliaryPow(blob, emptyList()) }
    }

    // Can get the global output indices of a transaction
    @Test
    fun testGetOutputIndices() {
        val txHashes = DaemonAssertions.getConfirmedTxHashes(daemon)
        assertTrue(txHashes.isNotEmpty(), "No confirmed txs found")

        for (txHash in txHashes) {
            val indices = daemon.getOutputIndices(txHash)
            assertTrue(indices.isNotEmpty())
            for (index in indices) assertTrue(index >= 0)
        }

        assertErrorMessage("Must provide a transaction hash") { daemon.getOutputIndices("") }
    }

    // Can get outputs given a list of output amounts and indices (binary). monero-cpp gives each output its transaction, with its hash and the
    // height of its block, but monero-c writes the outputs as a list of outputs, and an output is not written with its transaction: it has no
    // height. Remove the mark when monero-c writes them in their transactions and blocks, as it does the transactions of a wallet
    @Test
    @NotImplemented
    fun testGetOutputs() {
        val txHashes = DaemonAssertions.getConfirmedTxHashes(daemon)
        assertTrue(txHashes.isNotEmpty(), "No confirmed txs found")

        // identify each output by amount 0 (RingCT) and its global index
        val requested = ArrayList<MoneroOutput>()
        for (txHash in txHashes) {
            for (index in daemon.getOutputIndices(txHash)) {
                val output = MoneroOutput()
                output.amount = BigInteger.ZERO
                output.index = index
                requested.add(output)
            }
        }

        val numRequested = requested.size
        assertTrue(numRequested > 0)

        val fetched = daemon.getOutputs(requested)
        assertEquals(numRequested, fetched.size)

        for ((i, output) in fetched.withIndex()) {
            logger.fine("Testing fetched output: ${MoneroJson.serialize(output)}")
            assertEquals(requested[i].index, output.index)
            assertEquals(64, assertNotNull(output.stealthPublicKey).length)
            assertEquals(64, assertNotNull(output.mask).length)
            val tx = assertNotNull(output.tx)
            assertNotNull(assertNotNull(tx.block).height)
        }

        assertErrorMessage("Must provide outputs to fetch") { daemon.getOutputs(emptyList()) }
    }

    // Can get network (bandwidth) statistics
    @Test
    fun testGetNetworkStats() {
        DaemonAssertions.testNetworkStats(daemon.getNetworkStats())
    }

    // Can get public nodes known to the daemon
    @Test
    fun testGetPublicPeers() {
        val peers = daemon.getPublicPeers()
        assertEquals(0, peers.size)
        for (peer in peers) DaemonAssertions.testKnownPeer(peer, false)
    }

    // Can query the ban status of a single peer
    @Test
    fun testGetPeerBan() {
        val host = "192.168.1.60"
        val ban = MoneroBan()
        ban.host = host
        ban.isBanned = true
        ban.seconds = 60
        daemon.setPeerBan(ban)

        val banned = daemon.getPeerBan(host)
        assertEquals(true, banned.isBanned)
        assertTrue(assertNotNull(banned.seconds) > 0)

        assertEquals(false, daemon.getPeerBan("192.168.1.61").isBanned)

        assertErrorMessage("Must provide an address to check the ban status of") { daemon.getPeerBan("") }
    }

    // Can remove all listeners at once
    @Test
    fun testRemoveDaemonListeners() {
        val listeners = listOf(DaemonNotificationCollector(daemon), DaemonNotificationCollector(daemon))
        for (listener in listeners) daemon.addListener(listener)
        assertEquals(2, daemon.getListeners().size)

        daemon.removeListeners()
        assertEquals(0, daemon.getListeners().size)
    }

    // Can flush the blockchain to disk
    @Test
    fun testSaveBlockchain() {
        daemon.saveBlockchain()
    }

    // Can flush the daemon's invalid block and tx caches
    @Test
    fun testFlushCache() {
        daemon.flushCache()
        daemon.flushCache(true)
    }

    // Can disable the bootstrap daemon
    @Test
    fun testRemoveBootstrapDaemon() {
        daemon.removeBootstrapDaemon()
    }

    // Can set the poll period for block notifications
    @Test
    fun testSetPollPeriodInMs() {
        try {
            daemon.setPollPeriodInMs(1000)
        } finally {
            // restore the configured period so later tests poll at the expected rate
            daemon.setPollPeriodInMs(DAEMON_POLL_PERIOD_IN_MS)
        }
    }

    // Can set the daemon's log level
    @Test
    fun testSetLogLevel() {
        try {
            for (i in 0..4) daemon.setLogLevel(i)

            for (level in listOf(-1, 5)) {
                assertErrorMessage("Log level must be an integer between 0 and 4") { daemon.setLogLevel(level) }
            }
        } finally {
            // restore the level from docker-compose so later tests keep full logs
            daemon.setLogLevel(DAEMON_LOG_LEVEL)
        }
    }

    // Can set the daemon's log categories
    @Test
    fun testSetLogCategories() {
        try {
            val categories = daemon.setLogCategories("*:WARNING")
            assertNotNull(categories)
        } finally {
            // re-applying the configured level also resets categories to its mapping
            daemon.setLogLevel(DAEMON_LOG_LEVEL)
        }
    }

    // Can toggle the mining hash rate in the daemon log
    @Test
    fun testSetLogHashRate() {
        try {
            daemon.setLogHashRate(true)
            daemon.setLogHashRate(false)
        } catch (e: Exception) {
            // daemon rejects the call while not mining
            logger.warning("set_log_hash_rate: $e")
        }
    }

    // Can pop blocks from the top of the chain
    @Test
    fun testPopBlocks() {
        // stop mining so it doesn't race the pop, then restore the shared chain afterwards
        runCatching { daemon.stopMining() }
        val height = daemon.getHeight()

        assertEquals(height - 1, daemon.popBlocks(1))
        RegtestSupport.mineBlocks(1)
        assertEquals(height, daemon.getHeight())

        assertErrorMessage("Must provide a number of blocks to pop greater than 0") { daemon.popBlocks(0) }
    }

    //#endregion

    //#region Relays Tests

    // Can submit a tx in hex format to the pool and relay in one call
    @Test
    fun testSubmitAndRelayTxHex() {
        // wait one time for wallet txs in the pool to clear
        // TODO monero-project: update from pool does not prevent creating double spend tx
        DaemonTestChain.waitForTxsToClearPool(daemon)

        // create 2 txs, the second will double spend outputs of first
        //  TODO: this test requires tx to be from/to different accounts else
        // the occlusion issue (#4500) causes the tx to not be recognized by the wallet at all
        val tx1 = DaemonTestChain.getUnrelayedTx(2)
        val tx2 = DaemonTestChain.getUnrelayedTx(2)

        // submit and relay tx1
        val tx1Hash = assertNotNull(tx1.hash)
        var result = daemon.submitTxHex(assertNotNull(tx1.fullHex))
        assertEquals(true, result.isRelayed)
        DaemonAssertions.testSubmitTxResultGood(result)

        // tx1 is in the pool
        var txs = daemon.getTxPool()
        var found = false
        for (aTx in txs) {
            if (aTx.hash == tx1Hash) {
                assertEquals(true, aTx.isRelayed)
                found = true
                break
            }
        }

        assertTrue(found, "Tx1 was not found after being submitted to the daemon's tx pool")

        // tx1 is recognized by the wallet
        DaemonTestChain.wallet().sync()
        DaemonTestChain.wallet().getTx(tx1Hash)

        // submit and relay tx2 hex which double spends tx1
        result = daemon.submitTxHex(assertNotNull(tx2.fullHex))
        assertEquals(true, result.isRelayed)
        DaemonAssertions.testSubmitTxResultDoubleSpend(result)

        // tx2 is not in the pool
        txs = daemon.getTxPool()
        found = false
        for (aTx in txs) {
            if (aTx.hash == tx2.hash) {
                found = true
                break
            }
        }

        assertFalse(found, "Tx2 should not be in the pool because it double spends tx1 which is in the pool")
    }

    // Can submit a tx in hex format to the pool then relay
    @Test
    fun testSubmitThenRelayTxHex() {
        DaemonTestChain.waitForTxsToClearPool(daemon)
        val tx = DaemonTestChain.getUnrelayedTx(1)
        SubmitThenRelayTxTester(daemon, listOf(tx)).test()
    }

    // Can submit txs in hex format to the pool then relay
    @Test
    fun testSubmitThenRelayTxHexes() {
        DaemonTestChain.waitForTxsToClearPool(daemon)
        val txs = ArrayList<MoneroTx>()
        txs.add(DaemonTestChain.getUnrelayedTx(1))
        // TODO: accounts cannot be re-used across send tests else isRelayed is true; wallet needs to update?
        txs.add(DaemonTestChain.getUnrelayedTx(2))
        SubmitThenRelayTxTester(daemon, txs).test()
    }

    //#endregion

    //#region Test Notifications

    // Can start and stop listening
    @Test
    fun testStartStopListening() {
        // the daemon looks for a new block every 10 seconds, and a test that mines blocks one after the other does not wait for that
        daemon.setPollPeriodInMs(500)
        try {
            // add listener
            val listener = DaemonNotificationCollector(daemon)
            val autoRemoveListener = DaemonNotificationCollector(daemon, true)
            daemon.addListener(listener)
            daemon.addListener(autoRemoveListener)
            assertEquals(2, daemon.getListeners().size)

            // mine blocks and collect notifications, where monero-python starts the miner of the daemon and waits for 10 blocks
            repeat(10) {
                RegtestSupport.mineBlocks(1)
                Thread.sleep(700)
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (autoRemoveListener.numBlockHeaders == 0 && System.nanoTime() < deadline) Thread.sleep(50)

            // test notifications
            assertEquals(emptyList(), listener.errors)
            assertTrue(listener.numBlockHashes > 0, "Daemon listener didn't collect any block hash")
            assertTrue(listener.numBlockHeaders > 0, "Daemon listener didn't collect any block header")
            assertEquals(listener.numBlockHeaders, listener.numBlockHashes, "Count mismatch ${listener.numBlockHashes} != ${listener.numBlockHeaders}")

            // test autoremove listener
            assertEquals(emptyList(), autoRemoveListener.errors)
            assertEquals(1, autoRemoveListener.numBlockHeaders)
            assertEquals(1, autoRemoveListener.numBlockHashes)
            assertEquals(1, daemon.getListeners().size)

            // remove listener
            daemon.removeListener(listener)
            assertEquals(0, daemon.getListeners().size)
        } finally {
            daemon.removeListeners()
            daemon.setPollPeriodInMs(DAEMON_POLL_PERIOD_IN_MS)
        }
    }

    //#endregion

    //#region Kotlin

    // A block that the daemon does not have is an answer of the daemon with an error
    @Test
    fun testGetBlockByUnknownHash() {
        assertFailsWith<MoneroRpcError> { daemon.getBlockByHash("0".repeat(64)) }
    }

    // The timeout wait returns null when no block arrives in the time
    @Test
    fun testWaitForNextBlockHeaderTimesOut() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            assertNull(daemon.waitForNextBlockHeader(150), "no block is mined while the daemon waits")
        }
    }

    // The timeout wait returns the header of the block that is mined while it waits
    @Test
    fun testWaitForNextBlockHeader() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            val header = CompletableFuture<MoneroBlockHeader?>()
            Thread {
                try {
                    header.complete(daemon.waitForNextBlockHeader(30000))
                } catch (e: Throwable) {
                    header.completeExceptionally(e)
                }
            }.apply { isDaemon = true; start() }

            // give the wait the time to start, then mine for it
            Thread.sleep(500)
            RegtestSupport.mineBlocks(2)

            val arrived = header.get(35, TimeUnit.SECONDS)
            assertNotNull(arrived, "a block was mined while the daemon waited")
            assertTrue(arrived.height!! > 0, "the header of a mined block has a height")
        }
    }

    // The other calls of the daemon run while a timeout wait does
    @Test
    fun testCallsRunWhileTheDaemonWaits() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            val wait = CompletableFuture.supplyAsync { daemon.waitForNextBlockHeader(2000) }
            Thread.sleep(300)

            val started = System.nanoTime()
            assertTrue(daemon.getHeight() > 0)
            assertTrue(System.nanoTime() - started < TimeUnit.MILLISECONDS.toNanos(1500), "getHeight waited for the wait to time out")

            assertNull(wait.get(10, TimeUnit.SECONDS))
        }
    }

    // Close waits for a timeout wait that is running, and does not free the handle under it
    @Test
    fun testCloseWaitsForTheWaitThatRuns() {
        val daemon = MoneroDaemonRpc(RegtestSupport.daemonConnection())
        val wait = CompletableFuture.supplyAsync { daemon.waitForNextBlockHeader(1000) }
        Thread.sleep(300)

        daemon.close()

        assertNull(wait.get(10, TimeUnit.SECONDS), "the wait ended with its timeout, not with a crash")
    }

    // The wait throws once the daemon is closed
    @Test
    fun testWaitForNextBlockHeaderOnClosedDaemon() {
        val daemon = MoneroDaemonRpc(RegtestSupport.daemonConnection())
        daemon.close()
        assertFailsWith<MoneroError> { daemon.waitForNextBlockHeader(150) }
    }

    // A timeout out of range is an error of the C ABI
    @Test
    fun testWaitForNextBlockHeaderWithoutATimeout() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            assertFailsWith<MoneroError> { daemon.waitForNextBlockHeader(0) }
        }
    }

    //#endregion
}

// the period of the poll of the daemon and the log level of the daemon that the compose of src/test/docker sets, which a test that changes
// them restores
private const val DAEMON_POLL_PERIOD_IN_MS = 10000L
private const val DAEMON_LOG_LEVEL = 1
