package monero.wallet

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.wallet.model.MoneroIncomingTransfer
import monero.wallet.model.MoneroOutgoingTransfer
import monero.wallet.model.MoneroTransfer
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroTxQuery

/**
 * The transfers of a wallet, against a regtest node, like the tests of monero-python (tests/test_monero_wallet_common.py) that get the
 * transfers of a wallet and query them, which every kind of wallet runs: a subclass says which kind. They read the wallet of
 * [TxHistoryWallet], which has transfers between two accounts and a transaction in the pool. They need monero-c and the regtest node of
 * src/test/docker.
 */
abstract class BaseMoneroWalletTransfersTest {

    /**
     * The kind of wallet that the tests run for.
     */
    protected abstract val wallets: TestWallets

    @BeforeTest
    fun requireLibraryAndNode() {
        wallets.assumeAvailable()
    }

    private fun wallet(): MoneroWalletNative = TxHistoryWallet.get(wallets)

    // gets the transfers of a query, which have to be valid, and which the query does not change
    private fun getAndTest(query: MoneroTransferQuery?, expected: Boolean?): List<MoneroTransfer> {
        val copy = query?.copy()
        val transfers = wallet().getTransfers(query)
        when (expected) {
            true -> assertTrue(transfers.isNotEmpty(), "Transfers were expected but not found")
            false -> assertTrue(transfers.isEmpty())
            null -> {}
        }
        transfers.forEach { TxAssertions.validateTransfer(it) }
        if (query != null) assertEquals(MoneroJson.serialize(copy!!), MoneroJson.serialize(query), "the query is not changed")
        return transfers
    }

    private fun query(block: MoneroTransferQuery.() -> Unit): MoneroTransferQuery = MoneroTransferQuery().also(block)

    // Can get the transfers of the wallet, of an account, of a subaddress and of some subaddresses
    @Test
    fun testGetTransfers() {
        val wallet = wallet()
        getAndTest(null, true)
        var nonDefaultIncoming = false
        for (account in wallet.getAccounts(true)) {
            val accountTransfers = getAndTest(query { accountIndex = account.index }, null)
            assertTrue(accountTransfers.all { it.accountIndex == account.index })

            val subaddressTransfers = ArrayList<MoneroTransfer>()
            for (subaddress in account.subaddresses!!) {
                val transfers = getAndTest(query { accountIndex = subaddress.accountIndex; subaddressIndex = subaddress.index }, null)
                for (transfer in transfers) {
                    assertEquals(subaddress.accountIndex, transfer.accountIndex)
                    if (transfer is MoneroIncomingTransfer) {
                        assertEquals(subaddress.index, transfer.subaddressIndex)
                        if (transfer.accountIndex != 0 && transfer.subaddressIndex != 0) nonDefaultIncoming = true
                    } else {
                        transfer as MoneroOutgoingTransfer
                        assertTrue(subaddress.index in transfer.subaddressIndices!!)
                        if (transfer.accountIndex != 0 && transfer.subaddressIndices!!.any { it > 0 }) nonDefaultIncoming = true
                    }
                    // no duplicates: an outgoing transfer is found for each subaddress that it was sent from
                    if (subaddressTransfers.none { it.tx!!.hash == transfer.tx!!.hash && it.isIncoming == transfer.isIncoming && it.amount == transfer.amount }) {
                        subaddressTransfers.add(transfer)
                    }
                }
            }
            assertEquals(accountTransfers.size, subaddressTransfers.size)

            val indices = subaddressTransfers.flatMap {
                if (it is MoneroIncomingTransfer) listOf(it.subaddressIndex!!) else (it as MoneroOutgoingTransfer).subaddressIndices!!
            }.toSet()
            val transfers = getAndTest(query { accountIndex = account.index; subaddressIndices = indices.toList() }, null)
            assertEquals(subaddressTransfers.size, transfers.size)
            for (transfer in transfers) {
                assertEquals(account.index, transfer.accountIndex)
                if (transfer is MoneroIncomingTransfer) {
                    assertTrue(transfer.subaddressIndex in indices)
                } else {
                    assertTrue((transfer as MoneroOutgoingTransfer).subaddressIndices!!.any { it in indices }, "Subaddresses must overlap")
                }
            }
        }
        assertTrue(nonDefaultIncoming, "No transfers found in non-default account and subaddress")
    }

    // Can get transfers with additional configuration
    @Test
    fun testGetTransfersWithAQuery() {
        val wallet = wallet()

        // incoming transfers, and outgoing ones
        assertTrue(getAndTest(query { isIncoming = true }, true).all { it.isIncoming == true })
        assertTrue(getAndTest(query { isIncoming = false }, true).all { it.isOutgoing == true })

        // confirmed transfers to account 0
        val confirmed = getAndTest(query { accountIndex = 0; txQuery = MoneroTxQuery().also { it.isConfirmed = true } }, true)
        assertTrue(confirmed.all { it.accountIndex == 0 && it.tx!!.isConfirmed == true })

        // confirmed transfers to (1, 2)
        val toTheSubaddress = getAndTest(query { accountIndex = 1; subaddressIndex = 2; txQuery = MoneroTxQuery().also { it.isConfirmed = true } }, true)
        for (transfer in toTheSubaddress) {
            assertEquals(1, transfer.accountIndex)
            if (transfer is MoneroIncomingTransfer) assertEquals(2, transfer.subaddressIndex)
            else assertTrue(2 in (transfer as MoneroOutgoingTransfer).subaddressIndices!!)
            assertEquals(true, transfer.tx!!.isConfirmed)
        }

        // transfers in the pool
        val inThePool = getAndTest(query { txQuery = MoneroTxQuery().also { it.inTxPool = true } }, true)
        assertTrue(inThePool.all { it.tx!!.inTxPool == true })

        // transfers with a tx hash, and with some
        val hashes = wallet.getTxs().take(5).map { it.hash!! }
        assertTrue(hashes.size >= 3)
        for (hash in hashes) {
            val transfers = getAndTest(query { txQuery = MoneroTxQuery().also { it.hash = hash } }, true)
            assertTrue(transfers.all { it.tx!!.hash == hash })
        }
        val byHashes = getAndTest(query { txQuery = MoneroTxQuery().also { it.hashes = hashes } }, true)
        assertTrue(byHashes.all { it.tx!!.hash in hashes })

        // confirmed outgoing transfers that have destinations
        val withDestinations = getAndTest(query { isIncoming = false; hasDestinations = true; txQuery = MoneroTxQuery().also { it.isConfirmed = true } }, true)
        for (transfer in withDestinations) {
            assertTrue(transfer.isOutgoing == true)
            assertTrue((transfer as MoneroOutgoingTransfer).destinations!!.isNotEmpty())
            assertEquals(true, transfer.tx!!.isConfirmed)
        }

        // incoming transfers to the second account, which the wallet sent from its first account
        val fromTheWallet = wallet.getTransfers(query { accountIndex = 1; isIncoming = true; txQuery = MoneroTxQuery().also { it.isOutgoing = true } })
        assertTrue(fromTheWallet.isNotEmpty())
        for (transfer in fromTheWallet) {
            assertTrue(transfer.isIncoming == true)
            assertEquals(1, transfer.accountIndex)
            assertEquals(true, transfer.tx!!.isOutgoing)
            assertNull(transfer.tx!!.outgoingTransfer, "the transaction of the answer holds the incoming transfers only")
        }

        // incoming transfers to an address
        val address = wallet.getAddress(1, 0)
        val toTheAddress = wallet.getTransfers(query { isIncoming = true; this.address = address })
        assertTrue(toTheAddress.isNotEmpty())
        for (transfer in toTheAddress) {
            transfer as MoneroIncomingTransfer
            assertEquals(1, transfer.accountIndex)
            assertEquals(0, transfer.subaddressIndex)
            assertEquals(address, transfer.address)
        }
    }

    // A hash that is not a hash has no transfers, and one among the hashes of a query does not stop the others
    @Test
    fun testValidateInputsGetTransfers() {
        val wallet = wallet()
        assertTrue(wallet.getTransfers(query { txQuery = MoneroTxQuery().also { it.hash = "invalid_id" } }).isEmpty())

        val hash = wallet.getTxs().first().hash!!
        val transfers = wallet.getTransfers(query { txQuery = MoneroTxQuery().also { it.hashes = listOf(hash, "invalid_id") } })
        assertTrue(transfers.isNotEmpty())
        assertTrue(transfers.all { it.tx!!.hash == hash })

        // subaddresses that are not used
        assertTrue(wallet.getTransfers(query { accountIndex = 0; subaddressIndices = listOf(1234907) }).isEmpty())

        // a subaddress index that can not be
        assertFailsWith<MoneroError> { wallet.getTransfers(query { accountIndex = 0; subaddressIndex = -1 }) }
    }
}
