package monero.wallet

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroJson
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroTxWallet
import org.junit.jupiter.api.Tag

/**
 * The transactions of a full wallet, against a regtest node, like the tests of monero-python (tests/test_monero_wallet_common.py) that get
 * the transactions of a wallet by hash, by height, by payment id and with queries. They read the wallet of TxHistoryWallet, which has
 * transactions between two accounts, one with a payment id, and one in the pool. They need monero-c and the regtest node of src/test/docker.
 */
@Tag("integration")
class MoneroWalletFullTxsIntegrationTest {

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    private fun wallet(): MoneroWalletFull = TxHistoryWallet.get()

    private fun query(block: MoneroTxQuery.() -> Unit): MoneroTxQuery = MoneroTxQuery().also(block)

    // gets the transactions of a query, which have to be valid, and which the query does not change
    private fun getAndTest(query: MoneroTxQuery?, expected: Boolean?): List<MoneroTxWallet> {
        val copy = query?.let { MoneroJson.serialize(it) }
        val txs = wallet().getTxs(query)
        when (expected) {
            true -> assertTrue(txs.isNotEmpty(), "Transactions were expected but not found")
            false -> assertTrue(txs.isEmpty())
            null -> {}
        }
        txs.forEach { TxAssertions.validateTx(it) }
        if (query != null) assertEquals(copy, MoneroJson.serialize(query), "the query is not changed")
        return txs
    }

    // Can get the transactions of the wallet
    @Test
    fun testGetTxsWallet() {
        val txs = getAndTest(null, true)
        assertTrue(txs.size > 1, "Wallet has no txs to test")
        assertEquals(txs.size, txs.map { it.hash }.toSet().size, "a transaction is once in the wallet")

        val blockPerHeight = HashMap<Long, monero.daemon.model.MoneroBlock>()
        for (tx in txs) {
            // two copies of a transaction merge into a transaction that is the same
            val copy1 = tx.copy()
            val copy2 = tx.copy()
            if (copy1.isConfirmed == true) {
                copy1.block = tx.block!!.copy().also { it.txs = listOf(copy1) }
                copy2.block = tx.block!!.copy().also { it.txs = listOf(copy2) }
            }
            copy1.merge(copy2)
            TxAssertions.validateTx(copy1)
            assertEquals(MoneroJson.serialize(tx), MoneroJson.serialize(copy1), "the merge of two copies of a tx is the tx")

            // the transactions of a block hold the same block
            if (tx.isConfirmed == true) {
                val block = blockPerHeight.getOrPut(tx.height!!) { tx.block!! }
                assertTrue(block === tx.block, "Block references for same height must be same")
            }
        }

        // it is more than the miner txs, which only have an incoming transfer
        assertTrue(txs.any { it.isOutgoing == true })
        assertTrue(txs.any { it.isMinerTx == true })
        assertTrue(txs.any { it.inTxPool == true }, "The transaction in the pool is one of the wallet")
    }

    // Can get the transactions by their hash, and a hash that the wallet does not have is not one of the answer
    @Test
    fun testGetTxsByHash() {
        val wallet = wallet()
        val txs = wallet.getTxs().take(10)
        assertTrue(txs.size > 1, "Test requires at least 2 txs to fetch by hash")

        val fetched = wallet.getTx(txs[0].hash!!)
        assertNotNull(fetched)
        assertEquals(txs[0].hash, fetched.hash)
        TxAssertions.validateTx(fetched)

        val two = wallet.getTxs(listOf(txs[0].hash!!, txs[1].hash!!))
        assertEquals(2, two.size)

        // in the order of the hashes
        val hashes = txs.map { it.hash!! }.reversed()
        val reversed = wallet.getTxs(hashes)
        assertEquals(hashes, reversed.map { it.hash })
        reversed.forEach { TxAssertions.validateTx(it) }

        // with a hash that is missing
        val withMissing = wallet.getTxs(hashes + "d01ede9cde813b2a693069b640c4b99c5adbdb49fbbd8da2c16c8087d0c3e320")
        assertEquals(hashes, withMissing.map { it.hash })
    }

    // Can get the transactions with queries
    @Test
    fun testGetTxsWithQuery() {
        val wallet = wallet()
        val all = wallet.getTxs()

        // by hash, and by hashes
        val random = all.take(4)
        for (tx in random) {
            val txs = getAndTest(query { hash = tx.hash }, true)
            assertEquals(1, txs.size)
            // the transaction changes with the chain, so it merges with the one that was fetched before
            txs[0].merge(tx.copy())
            TxAssertions.validateTx(txs[0])
        }
        val byHashes = getAndTest(query { hashes = random.map { it.hash!! } }, true)
        assertEquals(random.size, byHashes.size)
        assertTrue(byHashes.all { it.hash in random.map { tx -> tx.hash } })

        // with an outgoing transfer, and without
        val outgoing = getAndTest(query { isOutgoing = true }, true)
        for (tx in outgoing) {
            assertEquals(true, tx.isOutgoing)
            assertNotNull(tx.outgoingTransfer)
        }
        assertTrue(getAndTest(query { isOutgoing = false }, true).all { it.outgoingTransfer == null })
        assertEquals(all.size, outgoing.size + wallet.getTxs(query { isOutgoing = false }).size)

        // with incoming transfers, and without
        val incoming = getAndTest(query { isIncoming = true }, true)
        for (tx in incoming) {
            assertEquals(true, tx.isIncoming)
            assertTrue(tx.incomingTransfers!!.isNotEmpty())
        }
        val notIncoming = getAndTest(query { isIncoming = false }, true)
        assertTrue(notIncoming.all { it.isIncoming == false && (it.incomingTransfers ?: emptyList()).isEmpty() })

        // associated with the second account
        val ofAccount = getAndTest(query { transferQuery = MoneroTransferQuery().also { it.accountIndex = 1 } }, true)
        for (tx in ofAccount) {
            val found = (tx.isOutgoing == true && tx.outgoingTransfer!!.accountIndex == 1) || (tx.incomingTransfers ?: emptyList()).any { it.accountIndex == 1 }
            assertTrue(found, "Transaction is not associated with account 1: ${MoneroJson.serialize(tx)}")
        }

        // confirmed, and with an outgoing transfer from the first account
        val confirmedOutgoing = getAndTest(query {
            isConfirmed = true
            transferQuery = MoneroTransferQuery().also { it.accountIndex = 0; it.isIncoming = false }
        }, true)
        for (tx in confirmedOutgoing) {
            assertEquals(true, tx.isConfirmed)
            assertEquals(true, tx.isOutgoing)
            assertEquals(0, tx.outgoingTransfer!!.accountIndex)
        }

        // with outgoing transfers that have destinations
        val withDestinations = getAndTest(query {
            isConfirmed = true
            transferQuery = MoneroTransferQuery().also { it.accountIndex = 0; it.hasDestinations = true }
        }, true)
        for (tx in withDestinations) {
            assertEquals(true, tx.isOutgoing)
            assertTrue(tx.outgoingTransfer!!.destinations!!.isNotEmpty())
        }

        // that include their outputs
        val withOutputs = getAndTest(query { includeOutputs = true }, true)
        assertTrue(withOutputs.any { it.getOutputsWallet().isNotEmpty() }, "No outputs found in txs")

        // with an output that is not spent, of an account and a subaddress that have one: which one the wallet spends depends on the chain
        val unspent = wallet.getOutputs(MoneroOutputQuery().also { it.isSpent = false; it.accountIndex = 1 }).first()
        val accountIdx = unspent.accountIndex
        val subaddressIdx = unspent.subaddressIndex
        val ofOutput = getAndTest(query { outputQuery = MoneroOutputQuery().also { it.isSpent = false; it.accountIndex = accountIdx; it.subaddressIndex = subaddressIdx } }, true)
        for (tx in ofOutput) {
            assertTrue(tx.getOutputsWallet().any { it.isSpent == false && it.accountIndex == accountIdx && it.subaddressIndex == subaddressIdx }, "Tx does not contain specified output")
        }

        // that are unlocked
        assertTrue(getAndTest(query { isLocked = false }, true).all { it.isLocked == false })

        // confirmed, sent to the same wallet, with a transfer with destinations
        val toItself = getAndTest(query {
            isIncoming = true
            isOutgoing = true
            includeOutputs = true
            isConfirmed = true
            transferQuery = MoneroTransferQuery().also { it.hasDestinations = true }
        }, true)
        for (tx in toItself) {
            assertEquals(true, tx.isIncoming)
            assertEquals(true, tx.isOutgoing)
            assertEquals(true, tx.isConfirmed)
            assertTrue(tx.getOutputsWallet().isNotEmpty())
            assertTrue(tx.outgoingTransfer!!.destinations!!.isNotEmpty())
        }

        // in the pool, and not
        assertTrue(getAndTest(query { inTxPool = true }, true).all { it.inTxPool == true })
        assertTrue(getAndTest(query { inTxPool = false }, true).none { it.inTxPool == true })
    }

    // Can get the transactions at a height, and between two heights
    @Test
    fun testGetTxsByHeight() {
        val wallet = wallet()
        val txs = getAndTest(query { isConfirmed = true }, true)

        // the height that most of them are at
        val heightCounts = txs.groupingBy { it.height!! }.eachCount()
        val modeHeight = heightCounts.maxByOrNull { it.value }!!.key
        val atMode = getAndTest(query { height = modeHeight }, true)
        assertEquals(heightCounts[modeHeight], atMode.size)
        assertTrue(atMode.all { it.height == modeHeight })

        // that range of one height
        val byRange = getAndTest(query { minHeight = modeHeight; maxHeight = modeHeight }, true)
        assertEquals(atMode.map { it.hash }.toSet(), byRange.map { it.hash }.toSet())

        // all of them by the range of the first and the last
        val heights = txs.map { it.height!! }
        val all = getAndTest(query { minHeight = heights.min(); maxHeight = heights.max() }, true)
        assertEquals(txs.map { it.hash }.toSet(), all.map { it.hash }.toSet())

        // some of them by a range that leaves out the lowest and the highest
        val min = heights.min() + 1
        val max = heights.max() - 1
        val filtered = getAndTest(query { minHeight = min; maxHeight = max }, true)
        assertTrue(filtered.size < txs.size)
        assertTrue(filtered.all { it.height!! in min..max })

        // a height that no transaction is at
        assertTrue(wallet.getTxs(query { height = heights.max() + 1000 }).isEmpty())
    }

    // Can get the transactions that have a payment id, and by their payment id
    @Test
    fun testGetTxsWithPaymentIds() {
        val wallet = wallet()
        val withPaymentId = getAndTest(query { hasPaymentId = true }, true)
        assertTrue(withPaymentId.all { !it.paymentId.isNullOrEmpty() })
        val paymentIds = withPaymentId.map { it.paymentId!! }
        assertTrue(TxHistoryWallet.PAYMENT_ID in paymentIds, "The tx to an integrated address has its payment id: $paymentIds")

        for (paymentId in paymentIds) {
            val txs = getAndTest(query { this.paymentId = paymentId }, true)
            assertTrue(txs.all { it.paymentId == paymentId })
        }

        val byPaymentIds = getAndTest(query { this.paymentIds = paymentIds }, true)
        assertTrue(byPaymentIds.all { it.paymentId in paymentIds })

        // the ones without are the others
        val without = getAndTest(query { hasPaymentId = false }, true)
        assertTrue(without.all { it.paymentId.isNullOrEmpty() })
        assertEquals(wallet.getTxs().size, withPaymentId.size + without.size)

        // a payment id that no transaction has
        assertTrue(wallet.getTxs(query { paymentId = "0000000000000000" }).isEmpty())
    }

    // Returns the fields of a transaction that are known, even if the query only asks for some of its transfers
    @Test
    fun testGetTxsFieldsWithFiltering() {
        val wallet = wallet()
        val txs = wallet.getTxs(query { isConfirmed = true })

        // a transaction that the wallet sent to one of its accounts from another
        for (tx in txs) {
            val outgoing = tx.outgoingTransfer ?: continue
            for (transfer in tx.incomingTransfers ?: emptyList()) {
                if (transfer.accountIndex == outgoing.accountIndex) continue

                val filtered = wallet.getTxs(query { transferQuery = MoneroTransferQuery().also { it.isIncoming = true; it.accountIndex = transfer.accountIndex } })
                assertTrue(filtered.size < wallet.getTxs().size, "the query filters the transactions")
                assertTrue(filtered.all { f -> f.incomingTransfers!!.any { it.accountIndex == transfer.accountIndex } })
                val same = filtered.firstOrNull { it.hash == tx.hash } ?: continue
                // what the filtered one has is the same as what the transaction has
                tx.copy().merge(same)
                assertEquals(tx.hash, same.hash)
                assertEquals(tx.fee, same.fee)
                assertEquals(tx.height, same.height)
                assertEquals(tx.isOutgoing, same.isOutgoing)
                assertNotNull(same.incomingTransfers)
                return
            }
        }
        throw AssertionError("Test requires tx sent from/to different accounts of same wallet but none found")
    }

    // A hash that the wallet does not have, or that is not a hash, is not among the transactions, and does not stop the others
    @Test
    fun testValidateInputsGetTxs() {
        val wallet = wallet()
        val hash = wallet.getTxs().first().hash!!
        val invalid = "invalid_id"
        val unknown1 = "6c4982f2499ece80e10b627083c4f9b992a00155e98bcba72a9588ccb91d0a61"
        val unknown2 = "ff397104dd875882f5e7c66e4f852ee134f8cf45e21f0c40777c9188bc92e943"

        assertNull(wallet.getTx(unknown1))
        assertTrue(wallet.getTxs(query { this.hash = unknown1 }).isEmpty())
        assertEquals(listOf(hash), wallet.getTxs(listOf(hash, unknown1)).map { it.hash })
        assertEquals(listOf(hash), wallet.getTxs(listOf(hash, unknown1, unknown2)).map { it.hash })

        assertNull(wallet.getTx(invalid))
        assertEquals(listOf(hash), wallet.getTxs(listOf(hash, invalid)).map { it.hash })
        assertEquals(listOf(hash), wallet.getTxs(listOf(hash, invalid, "invalid_hash_2")).map { it.hash })

        val txs = wallet.getTxs(query { hashes = listOf(hash, invalid, "invalid_hash_2") })
        assertEquals(1, txs.size)
        txs.forEach { TxAssertions.validateTx(it) }
    }
}
