package monero.daemon

import java.math.BigInteger
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
 * Monero daemon interface.
 */
interface MoneroDaemon {

    /**
     * Register a listener to receive daemon notifications.
     *
     * @param listener the listener to register
     */
    fun addListener(listener: MoneroDaemonListener)

    /**
     * Unregister a listener to receive daemon notifications.
     *
     * @param listener a previously registered listener to be unregistered
     */
    fun removeListener(listener: MoneroDaemonListener)

    /**
     * Get the listeners registered with the daemon.
     *
     * @return the registered listeners
     */
    fun getListeners(): List<MoneroDaemonListener>

    /**
     * Unregister all listeners registered with the daemon.
     */
    fun removeListeners()

    /**
     * Gets the version of the daemon.
     *
     * @return the version of the daemon
     */
    fun getVersion(): MoneroVersion

    /**
     * Indicates if the daemon is trusted or untrusted.
     *
     * @return true if the daemon is trusted, false otherwise
     */
    fun isTrusted(): Boolean

    /**
     * Get the number of blocks in the longest chain known to the node.
     *
     * @return the number of blocks
     */
    fun getHeight(): Long

    /**
     * Get a block's hash by its height.
     *
     * @param height is the height of the block hash to get
     * @return the block's hash at the given height
     */
    fun getBlockHash(height: Long): String

    /**
     * Get a block template for mining a new block.
     *
     * @param walletAddress is the address of the wallet to receive miner transactions if block is successfully mined
     * @param reserveSize is the reserve size (optional)
     * @return a block template for mining a new block
     */
    fun getBlockTemplate(walletAddress: String, reserveSize: Int? = null): MoneroBlockTemplate

    /**
     * Get the data needed to construct a block template for mining, e.g. for use by a pool that assembles its own block
     * templates.
     *
     * @return the current data for mining a new block
     */
    fun getMinerData(): MoneroMinerData

    /**
     * Calculate the proof-of-work hash of a mined block.
     *
     * @param majorVersion is the block's major version
     * @param height is the block's height
     * @param blockBlob is the block's blob to hash
     * @param seedHash is the seed hash used to select the RandomX dataset/cache
     * @return the block's proof-of-work hash
     */
    fun calculatePow(majorVersion: Int, height: Long, blockBlob: String, seedHash: String): String

    /**
     * Add auxiliary proof-of-work to a block template for merge mining, returning an updated block template whose Merkle
     * root commits to the given auxiliary blocks.
     *
     * @param blockTemplateBlob is the block template blob to add auxiliary PoW to
     * @param auxPow identifies each auxiliary chain by id and its block's PoW hash
     * @return the updated block template along with the (possibly reordered) auxiliary PoW
     */
    fun addAuxiliaryPow(blockTemplateBlob: String, auxPow: List<MoneroAuxiliaryPow>): MoneroAddAuxiliaryPowResult

    /**
     * Get the last block's header.
     *
     * @return the last block's header
     */
    fun getLastBlockHeader(): MoneroBlockHeader

    /**
     * Get a block header by its hash.
     *
     * @param blockHash is the hash of the block to get the header of
     * @return the block's header
     */
    fun getBlockHeaderByHash(blockHash: String): MoneroBlockHeader

    /**
     * Get a block header by its height.
     *
     * @param height is the height of the block to get the header of
     * @return the block's header
     */
    fun getBlockHeaderByHeight(height: Long): MoneroBlockHeader

    /**
     * Get block headers for the given range.
     *
     * @param startHeight is the start height lower bound inclusive
     * @param endHeight is the end height upper bound inclusive
     * @return block headers in the given range
     */
    fun getBlockHeadersByRange(startHeight: Long, endHeight: Long): List<MoneroBlockHeader>

    /**
     * Get a block by hash.
     *
     * @param blockHash is the hash of the block to get
     * @return the block with the given hash
     */
    fun getBlockByHash(blockHash: String): MoneroBlock

    /**
     * Get blocks by hash.
     *
     * @param blockHashes are array of hashes; first 10 blocks hash goes sequential, next goes in pow(2,n) offset, like
     *        2, 4, 8, 16, 32, 64 and so on, and the last one is always genesis block
     * @param startHeight is the start height to get blocks by hash
     * @param prune specifies if returned blocks should be pruned (defaults to false)
     * @param maxBlockCount is the maximum number of blocks to return (0 for the daemon's default)
     * @return the retrieved blocks, with the height of the chain of the daemon
     */
    fun getBlocksByHash(blockHashes: List<String>, startHeight: Long, prune: Boolean = false, maxBlockCount: Long = 0): MoneroGetBlocksByHashResult

    /**
     * Get a block by height.
     *
     * @param height is the height of the block to get
     * @return the block at the given height
     */
    fun getBlockByHeight(height: Long): MoneroBlock

    /**
     * Get blocks at the given heights.
     *
     * @param heights are the heights of the blocks to get
     * @return blocks at the given heights
     */
    fun getBlocksByHeight(heights: List<Long>): List<MoneroBlock>

    /**
     * Get blocks in the given height range.
     *
     * @param startHeight is the start height lower bound inclusive (optional)
     * @param endHeight is the end height upper bound inclusive (optional)
     * @return blocks in the given height range
     */
    fun getBlocksByRange(startHeight: Long? = null, endHeight: Long? = null): List<MoneroBlock>

    /**
     * Get blocks in the given height range as chunked requests so that each request is not too big.
     *
     * @param startHeight is the start height lower bound inclusive (optional)
     * @param endHeight is the end height upper bound inclusive (optional)
     * @param maxChunkSize is the maximum chunk size in any one request (default 3,000,000 bytes)
     * @return blocks in the given height range
     */
    fun getBlocksByRangeChunked(startHeight: Long? = null, endHeight: Long? = null, maxChunkSize: Long? = null): List<MoneroBlock>

    /**
     * Get block hashes as a binary request to the daemon.
     *
     * @param blockHashes specify block hashes to fetch; first 10 blocks hash goes sequential, next goes in pow(2,n)
     *        offset, like 2, 4, 8, 16, 32, 64 and so on, and the last one is always genesis block
     * @return the requested block hashes, with the height of the first one
     */
    fun getBlockHashes(blockHashes: List<String>): MoneroGetBlockHashesResult

    /**
     * Get a transaction by hash.
     *
     * @param txHash is the hash of the transaction to get
     * @param prune specifies if the returned tx should be pruned (defaults to false)
     * @return the transaction with the given hash or null if not found
     */
    fun getTx(txHash: String, prune: Boolean = false): MoneroTx?

    /**
     * Get transactions by hashes.
     *
     * @param txHashes are hashes of transactions to get
     * @param prune specifies if the returned txs should be pruned (defaults to false)
     * @return found transactions with the given hashes, in the order of the hashes
     */
    fun getTxs(txHashes: Collection<String>, prune: Boolean = false): List<MoneroTx>

    /**
     * Get a transaction hex by hash.
     *
     * @param txHash is the hash of the transaction to get hex from
     * @param prune specifies if the returned tx hex should be pruned (defaults to false)
     * @return the tx hex with the given hash
     */
    fun getTxHex(txHash: String, prune: Boolean = false): String

    /**
     * Get transaction hexes by hashes.
     *
     * @param txHashes are hashes of transactions to get hexes from
     * @param prune specifies if the returned tx hexes should be pruned (defaults to false)
     * @return are the tx hexes
     */
    fun getTxHexes(txHashes: Collection<String>, prune: Boolean = false): List<String>

    /**
     * Gets the total emissions and fees from the genesis block to the current height.
     *
     * @param height is the height to start computing the miner sum
     * @param numBlocks are the number of blocks to include in the sum
     * @return the sum emission and fees since the genesis block
     */
    fun getMinerTxSum(height: Long, numBlocks: Long): MoneroMinerTxSum

    /**
     * Get mining fee estimates per kB.
     *
     * @param graceBlocks is the number of blocks to leave before the fee estimate applies (defaults to 0)
     * @return mining fee estimates per kB
     */
    fun getFeeEstimate(graceBlocks: Long = 0): MoneroFeeEstimate

    /**
     * Submits a transaction to the daemon's pool.
     *
     * @param txHex is the raw transaction hex to submit
     * @param doNotRelay specifies if the tx should not be relayed (defaults to false)
     * @return the submission results
     */
    fun submitTxHex(txHex: String, doNotRelay: Boolean = false): MoneroSubmitTxResult

    /**
     * Relays a transaction by hash.
     *
     * @param txHash identifies the transaction to relay
     */
    fun relayTxByHash(txHash: String)

    /**
     * Relays transactions by hash.
     *
     * @param txHashes identify the transactions to relay
     */
    fun relayTxsByHash(txHashes: Collection<String>)

    /**
     * Get valid transactions seen by the node but not yet mined into a block, as well as spent key image information
     * for the tx pool.
     *
     * @return transactions in the transaction pool
     */
    fun getTxPool(): List<MoneroTx>

    /**
     * Get hashes of transactions in the transaction pool.
     *
     * @return hashes of transactions in the transaction pool
     */
    fun getTxPoolHashes(): List<String>

    /**
     * Get all transaction pool backlog.
     *
     * @return transaction pool backlog entries
     */
    fun getTxPoolBacklog(): List<MoneroTxBacklogEntry>

    /**
     * Get transaction pool statistics.
     *
     * @return statistics about the transaction pool
     */
    fun getTxPoolStats(): MoneroTxPoolStats

    /**
     * Flushes all transactions from the tx pool.
     */
    fun flushTxPool()

    /**
     * Flush a transaction from the tx pool.
     *
     * @param hash is the hash of the transaction to flush
     */
    fun flushTxPool(hash: String)

    /**
     * Flush transactions from the tx pool.
     *
     * @param hashes are hashes of transactions to flush
     */
    fun flushTxPool(hashes: Collection<String>)

    /**
     * Get the spent status of the given key image.
     *
     * @param keyImage is key image hex to get the status of
     * @return the status of the key image
     */
    fun getKeyImageSpentStatus(keyImage: String): MoneroKeyImageSpentStatus

    /**
     * Get the spent status of each given key image.
     *
     * @param keyImages are hex key images to get the statuses of
     * @return the spent status for each key image
     */
    fun getKeyImageSpentStatuses(keyImages: List<String>): List<MoneroKeyImageSpentStatus>

    /**
     * Get the global output index of each output in a transaction.
     *
     * @param txHash is the hash of the transaction to get output indices for
     * @return the global output index of each output in the transaction, in order
     */
    fun getOutputIndices(txHash: String): List<Long>

    /**
     * Get outputs identified by a list of output amounts and indices as a binary request.
     *
     * @param outputs identify each output by amount and index
     * @return the identified outputs
     */
    fun getOutputs(outputs: Collection<MoneroOutput>): List<MoneroOutput>

    /**
     * Get a histogram of output amounts. For all amounts (possibly filtered by parameters), gives the number of outputs
     * on the chain for that amount. RingCT outputs counts as 0 amount.
     *
     * @param amounts are amounts of outputs to make the histogram with
     * @param minCount is the minimum number of outputs of an amount to be in the histogram (optional)
     * @param maxCount is the maximum number of outputs of an amount to be in the histogram (optional)
     * @param isUnlocked makes a histogram with outputs with the specified lock state
     * @param recentCutoff is the number of blocks that count as recent (optional)
     * @return output histogram entries meeting the parameters
     */
    fun getOutputHistogram(
        amounts: Collection<BigInteger> = emptyList(),
        minCount: Int? = null,
        maxCount: Int? = null,
        isUnlocked: Boolean? = null,
        recentCutoff: Int? = null
    ): List<MoneroOutputHistogramEntry>

    /**
     * Creates an output distribution.
     *
     * @param amounts are amounts of outputs to make the distribution with
     * @param isCumulative specifies if the results should be cumulative (optional)
     * @param startHeight is the start height lower bound inclusive (optional)
     * @param endHeight is the end height upper bound inclusive (optional)
     * @return output distribution entries meeting the parameters
     */
    fun getOutputDistribution(
        amounts: Collection<BigInteger>,
        isCumulative: Boolean? = null,
        startHeight: Long? = null,
        endHeight: Long? = null
    ): List<MoneroOutputDistributionEntry>

    /**
     * Get general information about the state of the node and the network.
     *
     * @return general information about the node and network
     */
    fun getInfo(): MoneroDaemonInfo

    /**
     * Get synchronization information.
     *
     * @return contains sync information
     */
    fun getSyncInfo(): MoneroDaemonSyncInfo

    /**
     * Get network (bandwidth) statistics since the daemon started.
     *
     * @return the daemon's network statistics
     */
    fun getNetworkStats(): MoneroDaemonNetworkStats

    /**
     * Look up information regarding hard fork voting and readiness.
     *
     * @return hard fork information
     */
    fun getHardForkInfo(): MoneroHardForkInfo

    /**
     * Get alternative chains seen by the node.
     *
     * @return alternative chains seen by the node
     */
    fun getAltChains(): List<MoneroAltChain>

    /**
     * Get known block hashes which are not on the main chain.
     *
     * @return known block hashes which are not on the main chain
     */
    fun getAltBlockHashes(): List<String>

    /**
     * Get the download bandwidth limit.
     *
     * @return is the download bandwidth limit
     */
    fun getDownloadLimit(): Int

    /**
     * Set the download bandwidth limit.
     *
     * @param limit is the download limit to set (-1 to reset to default)
     * @return is the new download limit after setting
     */
    fun setDownloadLimit(limit: Int): Int

    /**
     * Reset the download bandwidth limit.
     *
     * @return the download bandwidth limit after resetting
     */
    fun resetDownloadLimit(): Int

    /**
     * Get the upload bandwidth limit.
     *
     * @return is the upload bandwidth limit
     */
    fun getUploadLimit(): Int

    /**
     * Set the upload bandwidth limit.
     *
     * @param limit is the upload limit to set (-1 to reset to default)
     * @return is the new upload limit after setting
     */
    fun setUploadLimit(limit: Int): Int

    /**
     * Reset the upload bandwidth limit.
     *
     * @return the upload bandwidth limit after resetting
     */
    fun resetUploadLimit(): Int

    /**
     * Get peers with active incoming or outgoing connections to the node.
     *
     * @return the daemon's peers
     */
    fun getPeers(): List<MoneroPeer>

    /**
     * Get all known peers including their last known online status.
     *
     * @return the daemon's known peers
     */
    fun getKnownPeers(): List<MoneroPeer>

    /**
     * Get public nodes known to the daemon.
     *
     * @param includeOffline specifies if offline nodes should be included (default false)
     * @return the daemon's known public nodes
     */
    fun getPublicPeers(includeOffline: Boolean = false): List<MoneroPeer>

    /**
     * Limit number of outgoing peers.
     *
     * @param limit is the maximum number of outgoing peers
     */
    fun setOutgoingPeerLimit(limit: Int)

    /**
     * Limit number of incoming peers.
     *
     * @param limit is the maximum number of incoming peers
     */
    fun setIncomingPeerLimit(limit: Int)

    /**
     * Get peer bans.
     *
     * @return entries about banned peers
     */
    fun getPeerBans(): List<MoneroBan>

    /**
     * Get the ban status of a peer node.
     *
     * @param address is the address of the peer node to check, e.g. "1.2.3.4" or "1.2.3.4:18080"
     * @return the peer's ban status
     */
    fun getPeerBan(address: String): MoneroBan

    /**
     * Ban a peer node.
     *
     * @param ban contains information about a node to ban
     */
    fun setPeerBan(ban: MoneroBan)

    /**
     * Ban peers nodes.
     *
     * @param bans are bans to apply against peer nodes
     */
    fun setPeerBans(bans: List<MoneroBan>)

    /**
     * Start mining.
     *
     * @param address is the address given miner rewards if the daemon mines a block
     * @param numThreads is the number of mining threads to run
     * @param isBackground specifies if the miner should run in the background or not
     * @param ignoreBattery specifies if the battery state (e.g. on laptop) should be ignored or not
     */
    fun startMining(address: String, numThreads: Long? = null, isBackground: Boolean? = null, ignoreBattery: Boolean? = null)

    /**
     * Stop mining.
     */
    fun stopMining()

    /**
     * Get the daemon's mining status.
     *
     * @return the daemon's mining status
     */
    fun getMiningStatus(): MoneroMiningStatus

    /**
     * Submit a mined block to the network.
     *
     * @param blockBlob is the mined block to submit
     */
    fun submitBlock(blockBlob: String)

    /**
     * Submit mined blocks to the network.
     *
     * @param blockBlobs are the mined blocks to submit
     */
    fun submitBlocks(blockBlobs: Collection<String>)

    /**
     * Prune the blockchain.
     *
     * @param check specifies to check the pruning (default false)
     * @return the prune result
     */
    fun pruneBlockchain(check: Boolean = false): MoneroPruneResult

    /**
     * Save (flush) the blockchain to disk.
     */
    fun saveBlockchain()

    /**
     * Pop (remove) blocks from the top of the blockchain.
     *
     * @param numBlocks is the number of blocks to pop
     * @return the blockchain height after popping the blocks
     */
    fun popBlocks(numBlocks: Long): Long

    /**
     * Flush the daemon's invalid block and transaction caches.
     *
     * @param badBlocks specifies to flush the bad blocks cache (default false)
     */
    fun flushCache(badBlocks: Boolean = false)

    /**
     * Set the bootstrap daemon used by the daemon to serve requests while it is not fully synced, e.g. a remote node.
     *
     * @param address is the bootstrap daemon's address (host:port), "auto" to select a public node automatically, or an
     *        empty string to disable the bootstrap daemon
     * @param username is the username to authenticate with the bootstrap daemon (optional)
     * @param password is the password to authenticate with the bootstrap daemon (optional)
     * @param proxy is the proxy used to reach the bootstrap daemon, e.g. a SOCKS proxy (optional)
     */
    fun setBootstrapDaemon(address: String, username: String = "", password: String = "", proxy: String = "")

    /**
     * Disable the bootstrap daemon so the daemon no longer falls back to it.
     */
    fun removeBootstrapDaemon()

    /**
     * Show or hide the mining hash rate in the daemon's console log.
     *
     * @param isVisible specifies if the hash rate should be logged
     */
    fun setLogHashRate(isVisible: Boolean)

    /**
     * Set the daemon's log level.
     *
     * @param level is the log level to set, from 0 (least verbose) to 4 (most verbose)
     */
    fun setLogLevel(level: Int)

    /**
     * Set the daemon's log categories.
     *
     * @param categories are the log categories to set, e.g. "*:WARNING,net.p2p:DEBUG" (an empty string resets
     *        categories to the default)
     * @return the daemon's resulting log categories
     */
    fun setLogCategories(categories: String = ""): String

    /**
     * Generate blocks and mine them to a wallet's address (regtest only).
     *
     * @param address is the address to mine the blocks to
     * @param numBlocks is the number of blocks to generate
     * @param prevBlockHash is the block hash to mine on top of, forking the chain if not the current tip (optional)
     * @param startingNonce is the starting nonce to use when mining the blocks (optional)
     * @return the result of generating the blocks
     */
    fun generateBlocks(address: String, numBlocks: Long, prevBlockHash: String? = null, startingNonce: Long? = null): MoneroGenerateBlocksResult

    /**
     * Check for update.
     *
     * @return the result of the update check
     */
    fun checkForUpdate(): MoneroDaemonUpdateCheckResult

    /**
     * Download an update.
     *
     * @param path is the path to download the update (optional)
     * @return the result of the update download
     */
    fun downloadUpdate(path: String = ""): MoneroDaemonUpdateDownloadResult

    /**
     * Safely disconnect and shut down the daemon.
     */
    fun stop()

    /**
     * Get the header of the next block added to the chain.
     *
     * @return the header of the next block added to the chain
     */
    fun waitForNextBlockHeader(): MoneroBlockHeader
}
