package monero.daemon

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.wallet.FullTestWallets
import monero.wallet.MoneroWalletNative
import monero.wallet.TxAssertions
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxWallet

/**
 * What the tests of the daemon need of the chain and of a wallet, which the tests of monero-python get from their setup: a chain that has
 * transactions in its last blocks and in the ones before, and a wallet with funds in two accounts that are not its first one, to make
 * transactions that are not relayed. The wallet is a full wallet that the JVM closes when it ends.
 */
object DaemonTestChain {

    // the number of transactions that the chain has to have, which the test of the spent key images asks for
    private const val NUM_TXS = 12

    // a coinbase output is spendable 60 blocks after its block
    private const val COINBASE_MATURITY = 60

    // what each output of the accounts 1 and 2 holds: 1 XMR in atomic units, which is more than a transaction needs
    private val FUNDS_PER_OUTPUT: BigInteger = BigInteger.valueOf(1_000_000_000_000L)

    // the nonce that the blocks of the alternative chain start from
    private const val ALT_CHAIN_STARTING_NONCE = 1_000_000L

    private var wallet: MoneroWalletNative? = null

    /**
     * Give the chain what the tests ask for, as the setup of monero-python does for its own: an alternative chain, which a reorganization
     * leaves in the daemon, and transactions from the accounts 1 and 2 of the wallet that a block confirms, which the blocks of the first
     * account pay for. More blocks follow them, so that they are in the last 100 blocks and more than 50 blocks below the last one. Each
     * call makes new ones, so the chain does not have to be what a test before this one left.
     */
    @Synchronized
    fun prepare() {
        val wallet = wallet()

        // an alternative chain, which the daemon keeps of the blocks that it replaced
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { reorgBlockchain(it) }

        // the funds: coinbase outputs of the first account, which pays the other two with an output for each transaction that the chain
        // keeps and two more. The blocks pay the primary address, since a daemon does not mine to a subaddress
        RegtestSupport.mineBlocks(6, wallet.getPrimaryAddress())
        RegtestSupport.mineBlocks(COINBASE_MATURITY + 10)
        wallet.sync()
        for (accountIdx in 1..2) {
            val config = MoneroTxConfig()
            config.accountIndex = 0
            config.destinations = List(NUM_TXS / 2 + 2) { MoneroDestination(wallet.getAddress(accountIdx, 0), FUNDS_PER_OUTPUT) }
            config.relay = true
            wallet.createTx(config)
        }

        // the outputs are spendable after 10 blocks
        RegtestSupport.mineBlocks(11)
        wallet.sync()

        // the transactions, which a block confirms
        for (i in 0 until NUM_TXS) {
            val config = MoneroTxConfig()
            config.accountIndex = 1 + i % 2
            config.address = wallet.getPrimaryAddress()
            config.amount = TxAssertions.MAX_FEE
            config.relay = true
            wallet.createTx(config)
        }
        RegtestSupport.mineBlocks(1)

        // the blocks that put them below the last ones
        RegtestSupport.mineBlocks(COINBASE_MATURITY)
        wallet.sync()
    }

    /**
     * Force a reorganization of the chain, which regtest does when it is asked to mine a longer chain from a block of the middle: it mines
     * a short chain on top of the last block, and then a longer one from the same block, which the daemon has to follow. The blocks of the
     * short chain are left as an alternative chain, as the reorganization of the setup of monero-python leaves them.
     *
     * @param daemon is the daemon that mines
     * @param numBlocks is the number of blocks of the short chain, and the long one has one more
     */
    fun reorgBlockchain(daemon: MoneroDaemon, numBlocks: Int = 3) {
        // the fork point: the last block that both chains have
        val forkHeader = daemon.getLastBlockHeader()
        val forkHash = checkNotNull(forkHeader.hash)
        val forkHeight = checkNotNull(forkHeader.height)

        // the original chain, which is the shorter
        val original = daemon.generateBlocks(RegtestSupport.MINING_ADDRESS, numBlocks.toLong(), forkHash)
        assertEquals(forkHeight + numBlocks, original.height, "Failed to build original chain")
        val originalHash = daemon.getBlockHash(forkHeight + 1)

        // the alternative chain, which has more work: its blocks start from another nonce, so they are not the ones of the original chain
        val alt = daemon.generateBlocks(RegtestSupport.MINING_ADDRESS, numBlocks + 1L, forkHash, ALT_CHAIN_STARTING_NONCE)
        assertEquals(forkHeight + numBlocks + 1, alt.height, "Failed to build alternate chain")

        // the reorganization happened: the active chain follows the heavier one, and the block of the shorter one is orphaned
        val activeHash = daemon.getBlockHash(forkHeight + 1)
        assertNotEquals(originalHash, activeHash, "Reorg failed: block after the fork point was not replaced by the alternate chain")
        assertTrue(activeHash in alt.blockHashes.orEmpty(), "Reorg failed: active chain block is not part of the generated alternate chain")
        assertEquals(true, daemon.getBlockHeaderByHash(originalHash).orphanStatus, "Reorg failed: original chain block was not marked as orphaned")
    }

    /**
     * The wallet of the tests, which is made on the first call.
     */
    @Synchronized
    fun wallet(): MoneroWalletNative {
        wallet?.let { return it }
        val created = FullTestWallets.create()
        try {
            created.createAccount()
            created.createAccount()
        } catch (e: Throwable) {
            runCatching { created.close() }
            throw e
        }
        Runtime.getRuntime().addShutdownHook(Thread { synchronized(this) { wallet?.let { runCatching { it.close() } } } })
        wallet = created
        return created
    }

    /**
     * Make a transaction that pays the wallet from an account of its own and is not relayed, which a test submits to the daemon. The
     * account is not the first one: a transaction that goes from an account to the same one is not seen by the wallet when the daemon takes
     * it from the pool. The transaction does not spend what it uses, so another one that is made from the same account spends the same
     * outputs.
     *
     * @param accountIdx is the account that pays, from 1 to 2
     */
    fun getUnrelayedTx(accountIdx: Int): MoneroTxWallet {
        assertTrue(accountIdx > 0, "Txs sent from/to same account are not properly synced from the pool")
        val wallet = wallet()
        val config = MoneroTxConfig()
        config.accountIndex = accountIdx
        config.address = wallet.getPrimaryAddress()
        config.amount = TxAssertions.MAX_FEE

        val tx = wallet.createTx(config)
        assertTrue(!tx.fullHex.isNullOrEmpty(), "The transaction has no hex")
        assertEquals(false, tx.relay, "Expected tx.relay to be false, got ${tx.relay}")
        return tx
    }

    /**
     * Wait for the transactions that were relayed to clear the pool, as the tracker of the transactions of the wallets of monero-python
     * does: a block takes them, which regtest mines when asked, and the wallet that made them is synced with it. The transactions that are
     * not relayed are left, since a test put them there and takes them out. A transaction that failed is flushed.
     *
     * @param daemon is the daemon whose pool is cleared
     */
    fun waitForTxsToClearPool(daemon: MoneroDaemon) {
        val wallet = wallet()
        wallet.sync()
        for (tx in daemon.getTxPool()) {
            if (tx.isFailed == true) daemon.flushTxPool(tx.hash!!)
        }
        if (daemon.getTxPool().any { it.isRelayed == true }) RegtestSupport.mineBlocks(1)
        wallet.sync()
    }
}
