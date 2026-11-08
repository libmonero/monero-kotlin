package monero.wallet

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.wallet.TxAssertions.MAX_FEE
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxWallet

/**
 * The transactions that a wallet sends and that are locked, which it tracks as the blocks are added to the chain, against a regtest
 * node, like the tests of monero-python (tests/test_monero_wallet_common.py, test_update_locked_*, over SendAndUpdateTxsTester) that send
 * from an account to itself and to another one, split or not, which every kind of wallet runs: a subclass says which kind. They need
 * monero-c and the regtest node of src/test/docker.
 */
abstract class BaseMoneroWalletUpdateLockedTest {

    /**
     * The kind of wallet that the tests run for.
     */
    protected abstract val wallets: TestWallets

    @BeforeTest
    fun requireLibraryAndNode() {
        wallets.assumeAvailable()
    }

    // a wallet with five coinbases that matured and a second account
    private fun fundedWallet(): MoneroWalletNative {
        val wallet = wallets.create()
        try {
            RegtestSupport.mineBlocks(5, wallet.getPrimaryAddress())
            RegtestSupport.mineBlocks(130)
            wallet.sync()
            wallet.createAccount()
            return wallet
        } catch (e: Throwable) {
            wallet.close()
            throw e
        }
    }

    /**
     * Send the transactions that the config says, test them, and test them again as each of the next blocks is mined, until they have two
     * confirmations, like SendAndUpdateTxsTester. Then mine until they are unlocked. The wallet pays the fee, so the amount is a fee.
     */
    private fun testSendAndUpdateTxs(toAccount: Int, split: Boolean) {
        fundedWallet().use { wallet ->
            val config = MoneroTxConfig()
            config.address = wallet.getAddress(toAccount, 0)
            config.amount = MAX_FEE
            config.accountIndex = 0
            config.canSplit = split
            config.relay = true
            val sent = wallet.createTxs(config)
            assertTrue(sent.isNotEmpty())
            if (!split) assertEquals(1, sent.size)

            for (tx in sent) {
                TxAssertions.validateSent(tx, true)
                assertEquals(false, tx.isConfirmed)
                assertEquals(true, tx.inTxPool)
            }
            val hashes = sent.map { it.hash!! }

            // track the transactions as the blocks are added
            var confirmations = 0L
            while (confirmations < 2) {
                RegtestSupport.mineBlocks(1)
                wallet.sync()
                val fetched = fetch(wallet, hashes)
                fetched.forEach { checkTracked(it, toAccount) }

                // what the wallet knows of the transactions merges into what the send answered, which then says that they are confirmed. The merge
                // leaves their block out, as monero-python knows, which copies it for its tests
                for (tx in fetched) {
                    for (sentTx in sent.filter { it.hash == tx.hash && it.isOutgoing == tx.isOutgoing }) {
                        sentTx.merge(tx.copy())
                        assertEquals(true, sentTx.isConfirmed)
                        assertEquals(tx.numConfirmations, sentTx.numConfirmations)
                        assertEquals(tx.fee, sentTx.fee)
                    }
                }

                confirmations = fetched.first().numConfirmations!!
            }

            // they are locked until the blocks that the chain asks for are mined
            fetch(wallet, hashes).forEach { assertEquals(true, it.isLocked, "locked with $confirmations confirmations") }
            RegtestSupport.mineBlocks(10)
            wallet.sync()
            val unlocked = fetch(wallet, hashes)
            unlocked.forEach {
                assertEquals(false, it.isLocked)
                assertEquals(true, it.isConfirmed)
                assertTrue(it.numConfirmations!! >= 12)
            }
        }
    }

    private fun fetch(wallet: MoneroWalletNative, hashes: List<String>): List<MoneroTxWallet> {
        val fetched = wallet.getTxs(hashes)
        assertTrue(fetched.isNotEmpty())
        assertEquals(hashes.toSet(), fetched.map { it.hash }.toSet())
        return fetched
    }

    // A transaction that the wallet sent is outgoing from the account 0. When it pays another account, it is incoming to that account too. When
    // it pays the account that it comes from, the wallet has only the outgoing side of it, which monero-python knows as issue 4500 of monero:
    // the incoming transactions are occluded by their outgoing counterpart
    private fun checkTracked(tx: MoneroTxWallet, toAccount: Int) {
        TxAssertions.validateTx(tx)
        assertEquals(true, tx.isOutgoing)
        assertEquals(0, tx.outgoingTransfer!!.accountIndex)
        assertNotNull(tx.fee)
        val incoming = tx.incomingTransfers ?: emptyList()
        if (toAccount != 0) assertTrue(incoming.any { it.accountIndex == toAccount }, "the transaction is incoming to the account $toAccount")
        else assertTrue(incoming.all { it.accountIndex == 0 })
    }

    // Can update a locked tx sent from/to the same account as blocks are added to the chain
    @Test
    fun testUpdateLockedSameAccount() = testSendAndUpdateTxs(0, false)

    // Can update split locked txs sent from/to the same account as blocks are added to the chain
    @Test
    fun testUpdateLockedSameAccountSplit() = testSendAndUpdateTxs(0, true)

    // Can update a locked tx sent from/to different accounts as blocks are added to the chain
    @Test
    fun testUpdateLockedDifferentAccounts() = testSendAndUpdateTxs(1, false)

    // Can update locked, split txs sent from/to different accounts as blocks are added to the chain
    @Test
    fun testUpdateLockedDifferentAccountsSplit() = testSendAndUpdateTxs(1, true)
}
