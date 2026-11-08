package monero.wallet

import java.math.BigInteger
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.ModelTestSupport
import monero.wallet.model.IncomingTransferComparator
import monero.wallet.model.MoneroAccount
import monero.wallet.model.MoneroIncomingTransfer
import monero.wallet.model.MoneroOutgoingTransfer
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroSubaddress
import monero.wallet.model.MoneroTransfer
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroTxWallet

/**
 * Compares two wallets with what the chain says of them, like WalletEqualityUtils of monero-python (tests/utils/wallet_equality_utils.py):
 * the keys, the height, the balances, and the accounts, the subaddresses, the transactions, the transfers and the outputs that are
 * confirmed. What only a wallet knows is left out: the notes, the labels, the tags, the addresses that a transfer was sent to, and the
 * destinations of a transaction when the other wallet cannot know them, as it did not send it.
 *
 * The models have no equality, so two of them are the same when their JSON is. These differ from the utilities of Python, which clear the
 * list of the subaddresses of an account before they compare it, so that nothing is compared, and look at the wrong subaddress when one
 * list is longer than the other.
 */
object WalletEquality {

    /**
     * Compare two wallets, which have no transaction in the pool: the transactions that are in it are not on the chain yet, so the balances
     * would differ by what they move. The wallets sync until they are at the same height.
     */
    fun assertOnChain(w1: MoneroWalletNative, w2: MoneroWalletNative) {
        assertEquals(w1.isConnectedToDaemon(), w2.isConnectedToDaemon())
        if (w1.isConnectedToDaemon()) {
            while (w1.getHeight() != w2.getHeight()) {
                w1.sync()
                w2.sync()
            }
            val poolQuery = MoneroTxQuery().also { it.inTxPool = true }
            assertEquals(emptyList(), w1.getTxs(poolQuery).map { it.hash }, "the first wallet has transactions in the pool")
            assertEquals(emptyList(), w2.getTxs(poolQuery).map { it.hash }, "the second wallet has transactions in the pool")
        }

        assertEquals(w1.getHeight(), w2.getHeight())
        assertEquals(w1.getSeed(), w2.getSeed())
        assertEquals(w1.getPrimaryAddress(), w2.getPrimaryAddress())
        assertEquals(w1.getPrivateViewKey(), w2.getPrivateViewKey())
        assertEquals(w1.getPrivateSpendKey(), w2.getPrivateSpendKey())

        val txQuery = MoneroTxQuery().also { it.isConfirmed = true }
        assertTxsEqual(w1.getTxs(txQuery), w2.getTxs(txQuery))
        txQuery.includeOutputs = true
        assertTxsEqual(w1.getTxs(txQuery), w2.getTxs(txQuery))
        assertAccountsEqual(w1.getAccounts(true), w2.getAccounts(true))
        assertEquals(w1.getBalance(), w2.getBalance())
        assertEquals(w1.getUnlockedBalance(), w2.getUnlockedBalance())

        val transferQuery = MoneroTransferQuery().also { it.txQuery = MoneroTxQuery().also { query -> query.isConfirmed = true } }
        assertTransfersEqual(w1.getTransfers(transferQuery), w2.getTransfers(transferQuery))
        val outputQuery = MoneroOutputQuery().also { it.txQuery = MoneroTxQuery().also { query -> query.isConfirmed = true } }
        assertOutputsEqual(w1.getOutputs(outputQuery), w2.getOutputs(outputQuery))
    }

    /**
     * Compare two full wallets: what [assertOnChain] compares, and the network, the restore height, the connection to the daemon and the
     * language of the seed.
     */
    fun assertFullOnChain(w1: MoneroWalletNative, w2: MoneroWalletNative) {
        assertOnChain(w1, w2)
        assertEquals(w1.getNetworkType(), w2.getNetworkType())
        assertEquals(w1.getRestoreHeight(), w2.getRestoreHeight())
        // the connection has also what the last check of it found, which differs between two wallets
        val connection1 = assertNotNull(w1.getDaemonConnection())
        val connection2 = assertNotNull(w2.getDaemonConnection())
        assertEquals(connection1.uri, connection2.uri)
        assertEquals(connection1.username, connection2.username)
        assertEquals(connection1.password, connection2.password)
        assertEquals(w1.getSeedLanguage(), w2.getSeedLanguage())
    }

    // ------------------------------- ACCOUNTS ----------------------------------

    fun assertAccountsEqual(accounts1: List<MoneroAccount>, accounts2: List<MoneroAccount>) {
        for (i in 0 until max(accounts1.size, accounts2.size)) {
            when {
                i < accounts1.size && i < accounts2.size -> assertAccountEqual(accounts1[i], accounts2[i])
                // a wallet that has more accounts has unused ones after the last that the other has
                i >= accounts1.size -> return assertUnused(accounts2, i)
                else -> return assertUnused(accounts1, i)
            }
        }
    }

    private fun assertUnused(accounts: List<MoneroAccount>, from: Int) {
        for (j in from until accounts.size) {
            assertEquals(BigInteger.ZERO, accounts[j].balance, "the balance of the account ${accounts[j].index}")
            val subaddresses = assertNotNull(accounts[j].subaddresses)
            assertTrue(subaddresses.isNotEmpty())
            subaddresses.forEach { assertEquals(false, it.isUsed) }
        }
    }

    private fun assertAccountEqual(account1: MoneroAccount, account2: MoneroAccount) {
        // the data that only a wallet has is left out, and the subaddresses are compared on their own
        val subaddresses1 = account1.subaddresses.orEmpty()
        val subaddresses2 = account2.subaddresses.orEmpty()
        account1.subaddresses = null
        account2.subaddresses = null
        account1.tag = null
        account2.tag = null
        assertSameJson(account1, account2, "the account ${account1.index}")
        assertSubaddressesEqual(subaddresses1, subaddresses2)
    }

    private fun assertSubaddressesEqual(subaddresses1: List<MoneroSubaddress>, subaddresses2: List<MoneroSubaddress>) {
        for (i in 0 until max(subaddresses1.size, subaddresses2.size)) {
            when {
                i < subaddresses1.size && i < subaddresses2.size -> assertSubaddressEqual(subaddresses1[i], subaddresses2[i])
                // one wallet has subaddresses past the last of the other, which are unused
                i >= subaddresses1.size -> return assertUnusedSubaddresses(subaddresses2, i)
                else -> return assertUnusedSubaddresses(subaddresses1, i)
            }
        }
    }

    private fun assertUnusedSubaddresses(subaddresses: List<MoneroSubaddress>, from: Int) {
        for (j in from until subaddresses.size) {
            assertEquals(BigInteger.ZERO, subaddresses[j].balance)
            assertEquals(false, subaddresses[j].isUsed)
        }
    }

    private fun assertSubaddressEqual(subaddress1: MoneroSubaddress, subaddress2: MoneroSubaddress) {
        subaddress1.label = null
        subaddress2.label = null
        assertSameJson(subaddress1, subaddress2, "the subaddress ${subaddress1.accountIndex}:${subaddress1.index}")
    }

    // ------------------------------- TRANSACTIONS ----------------------------------

    /**
     * Compare the transactions that are on the chain, which are the ones that are not in the pool and did not fail.
     */
    fun assertTxsEqual(txs1: List<MoneroTxWallet>, txs2: List<MoneroTxWallet>) {
        val onChain1 = txs1.filter { it.inTxPool != true && it.isFailed != true }
        val onChain2 = txs2.filter { it.inTxPool != true && it.isFailed != true }
        for (tx in onChain1 + onChain2) {
            tx.note = null
            tx.outgoingTransfer?.addresses = emptyList()
        }
        assertEquals(onChain1.size, onChain2.size)

        for (tx1 in onChain1) {
            val tx2 = assertNotNull(onChain2.singleOrNull { it.hash == tx1.hash }, "Tx ${tx1.hash} not found")

            // the destinations are known by the wallet that sent the transaction, so the other gets them for the comparison
            val destinations1 = tx1.outgoingTransfer?.destinations.orEmpty()
            val destinations2 = tx2.outgoingTransfer?.destinations.orEmpty()
            if (destinations1.isNotEmpty()) {
                if (destinations2.isEmpty()) transferCachedInfo(tx1, tx2)
            } else if (destinations2.isNotEmpty()) {
                transferCachedInfo(tx2, tx1)
            }

            assertTrue(mergeable(tx1, tx2), "Txs are not mergeable: ${ModelTestSupport.write(tx1)}, ${ModelTestSupport.write(tx2)}")
            assertSameJson(tx1, tx2, "the tx ${tx1.hash}")

            // the block is not in the JSON of the transaction, and the transactions of a block are in the order that the wallet found them
            val block1 = assertNotNull(tx1.block)
            val block2 = assertNotNull(tx2.block)
            val blockTxs1 = block1.txs
            val blockTxs2 = block2.txs
            block1.txs = emptyList()
            block2.txs = emptyList()
            try {
                assertSameJson(block1, block2, "the block of the tx ${tx1.hash}")
            } finally {
                block1.txs = blockTxs1
                block2.txs = blockTxs2
            }
            assertEquals(blockTxs1.orEmpty().map { it.hash }, blockTxs2.orEmpty().map { it.hash }, "the transactions of the block of ${tx1.hash}")
        }
    }

    // whether a transaction merges into the other, as the copies of two fetches of it do
    private fun mergeable(tx1: MoneroTxWallet, tx2: MoneroTxWallet): Boolean {
        return try {
            val copy1 = tx1.copy()
            val copy2 = tx2.copy()
            copy1.block = tx1.block!!.copy().also { it.txs = listOf(copy1) }
            copy2.block = tx2.block!!.copy().also { it.txs = listOf(copy2) }
            copy1.merge(copy2)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Copy to a transaction what a wallet knows of it because it sent it: the incoming transfers to the account that sent it, the outgoing
     * transfer with its destinations, and the payment id, which the server does not give for an outgoing transfer that it does not have in
     * its cache (monero-project issue 8378).
     */
    private fun transferCachedInfo(src: MoneroTxWallet, tgt: MoneroTxWallet) {
        val incoming = src.incomingTransfers.orEmpty()
        if (incoming.isNotEmpty()) {
            val sentFrom = assertNotNull(src.outgoingTransfer).accountIndex
            val added = incoming.filter { it.accountIndex == sentFrom }
            tgt.incomingTransfers = (tgt.incomingTransfers.orEmpty() + added).sortedWith(IncomingTransferComparator())
        }

        val outgoing = tgt.outgoingTransfer
        if (outgoing == null) {
            tgt.outgoingTransfer = src.outgoingTransfer
        } else {
            val from = assertNotNull(src.outgoingTransfer)
            outgoing.destinations = from.destinations.orEmpty().toList()
            outgoing.amount = from.amount
        }
        if (tgt.outgoingTransfer != null) tgt.paymentId = src.paymentId
    }

    // ------------------------------- TRANSFERS ----------------------------------

    /**
     * Compare the transfers, which a wallet gives in ascending order by the height of its transaction, and together for each transaction.
     */
    fun assertTransfersEqual(transfers1: List<MoneroTransfer>, transfers2: List<MoneroTransfer>) {
        assertEquals(transfers1.size, transfers2.size)
        val perTx1 = LinkedHashMap<String, MutableList<MoneroTransfer>>()
        val perTx2 = LinkedHashMap<String, MutableList<MoneroTransfer>>()
        var lastHeight: Long? = null
        var lastHash1: String? = null
        var lastHash2: String? = null

        for (i in transfers1.indices) {
            val transfer1 = transfers1[i]
            val transfer2 = transfers2[i]
            val tx1 = assertNotNull(transfer1.tx)
            val tx2 = assertNotNull(transfer2.tx)

            // the transfers have the same height even if they are not of the same transaction, since wallet2 does not order the transactions of a block
            assertEquals(tx1.height, tx2.height)
            val height = assertNotNull(tx1.height)
            lastHeight?.let { assertTrue(it <= height, "the transfers are not in ascending order by height") }
            lastHeight = height

            val hash1 = assertNotNull(tx1.hash)
            val hash2 = assertNotNull(tx2.hash)
            // the transfers of a transaction are together, so a transaction that is seen again is a transaction that comes back
            if (hash1 != lastHash1) {
                assertFalse(perTx1.containsKey(hash1), "the transfers of $hash1 are not together")
                lastHash1 = hash1
            }
            if (hash2 != lastHash2) {
                assertFalse(perTx2.containsKey(hash2), "the transfers of $hash2 are not together")
                lastHash2 = hash2
            }
            perTx1.getOrPut(hash1) { ArrayList() }.add(transfer1)
            perTx2.getOrPut(hash2) { ArrayList() }.add(transfer2)
        }
        compareTransfers(perTx1, perTx2)
    }

    private fun compareTransfers(perTx1: Map<String, List<MoneroTransfer>>, perTx2: Map<String, List<MoneroTransfer>>) {
        for ((hash, transfers1) in perTx1) {
            val transfers2 = assertNotNull(perTx2[hash], "no transfers of $hash")
            assertEquals(transfers1.size, transfers2.size)
            for (i in transfers1.indices) {
                val transfer1 = transfers1[i]
                val transfer2 = transfers2[i]
                if (transfer1 is MoneroOutgoingTransfer) {
                    assertTrue(transfer2 is MoneroOutgoingTransfer)
                    transfer2 as MoneroOutgoingTransfer
                    // the destinations are known by the wallet that sent the transfer
                    if (transfer1.destinations.orEmpty().isNotEmpty()) {
                        if (transfer2.destinations.orEmpty().isEmpty()) transferCachedInfo(assertNotNull(transfer1.tx), assertNotNull(transfer2.tx))
                    } else if (transfer2.destinations.orEmpty().isNotEmpty()) {
                        transferCachedInfo(assertNotNull(transfer2.tx), assertNotNull(transfer1.tx))
                    }
                    transfer1.addresses = emptyList()
                    transfer2.addresses = emptyList()
                } else {
                    assertTrue(transfer1 is MoneroIncomingTransfer)
                    assertTrue(transfer2 is MoneroIncomingTransfer)
                    (transfer1 as MoneroIncomingTransfer).address = null
                    (transfer2 as MoneroIncomingTransfer).address = null
                }
                assertSameJson(transfer1, transfer2, "the transfer $i of $hash")
            }
        }
    }

    // ------------------------------- OUTPUTS ----------------------------------

    /**
     * Compare the outputs, which a wallet gives in ascending order by the height of its transaction, and together for each transaction.
     */
    fun assertOutputsEqual(outputs1: List<MoneroOutputWallet>, outputs2: List<MoneroOutputWallet>) {
        assertEquals(outputs1.size, outputs2.size)
        val perTx1 = LinkedHashMap<String, MutableList<MoneroOutputWallet>>()
        val perTx2 = LinkedHashMap<String, MutableList<MoneroOutputWallet>>()
        var lastHeight: Long? = null
        var lastHash1: String? = null
        var lastHash2: String? = null

        for (i in outputs1.indices) {
            val output1 = outputs1[i]
            val output2 = outputs2[i]
            val tx1 = assertNotNull(output1.tx)
            val tx2 = assertNotNull(output2.tx)

            assertEquals(tx1.height, tx2.height)
            val height = assertNotNull(tx1.height)
            lastHeight?.let { assertTrue(it <= height, "the outputs are not in ascending order by height") }
            lastHeight = height

            val hash1 = assertNotNull(tx1.hash)
            val hash2 = assertNotNull(tx2.hash)
            if (hash1 != lastHash1) {
                assertFalse(perTx1.containsKey(hash1), "the outputs of $hash1 are not together")
                lastHash1 = hash1
            }
            if (hash2 != lastHash2) {
                assertFalse(perTx2.containsKey(hash2), "the outputs of $hash2 are not together")
                lastHash2 = hash2
            }
            perTx1.getOrPut(hash1) { ArrayList() }.add(output1)
            perTx2.getOrPut(hash2) { ArrayList() }.add(output2)
        }

        for ((hash, outputsOfTx1) in perTx1) {
            val outputsOfTx2 = assertNotNull(perTx2[hash], "no outputs of $hash")
            assertEquals(outputsOfTx1.size, outputsOfTx2.size)
            for (i in outputsOfTx1.indices) {
                assertEquals(outputsOfTx1[i].tx!!.hash, outputsOfTx2[i].tx!!.hash)
                assertSameJson(outputsOfTx1[i], outputsOfTx2[i], "the output $i of $hash")
            }
        }
    }

    // the models have no equality: they are the same when their JSON is
    private fun assertSameJson(expected: Any, actual: Any, what: String) {
        val diff = ModelTestSupport.diff(ModelTestSupport.tree(ModelTestSupport.write(expected)), ModelTestSupport.tree(ModelTestSupport.write(actual)))
        assertNull(diff, "$what differs: $diff")
    }
}
