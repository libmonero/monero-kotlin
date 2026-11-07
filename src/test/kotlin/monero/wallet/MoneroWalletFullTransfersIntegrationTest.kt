package monero.wallet

import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroIncomingTransfer
import monero.wallet.model.MoneroOutgoingTransfer
import monero.wallet.model.MoneroTransfer
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag

/**
 * The transfers of a full wallet, against a regtest node, like the tests of monero-python (tests/test_monero_wallet_common.py) that get
 * the transfers of a wallet and query them. The wallet of these tests has what those tests need and a fresh regtest wallet has not:
 * a second account with subaddresses that received funds from the first, transfers between the two accounts, and a transaction that is
 * in the pool. It is made once for all the tests, which only read it. They need monero-c and the regtest node of src/test/docker.
 */
@Tag("integration")
class MoneroWalletFullTransfersIntegrationTest {

    companion object {
        private var directory: Path? = null
        private var shared: MoneroWalletFull? = null

        @JvmStatic
        @AfterAll
        fun closeTheWallet() {
            shared?.close()
            shared = null
            directory?.toFile()?.deleteRecursively()
            directory = null
        }
    }

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    private fun wallet(): MoneroWalletFull = shared ?: build().also { shared = it }

    // Five coinbases that matured go to the first account, which pays the second one with one transaction to three of its subaddresses, then
    // pays one of its own subaddresses, then the second account pays the first one, and the last payment is left in the pool.
    private fun build(): MoneroWalletFull {
        val dir = Files.createTempDirectory("transfers-")
        directory = dir
        val config = MoneroWalletConfig()
        config.path = dir.resolve("wallet").toString()
        config.password = "supersecretpassword123"
        config.networkType = MoneroNetworkType.MAINNET
        config.server = RegtestSupport.daemonConnection()
        config.regtest = true
        val wallet = MoneroWalletFull.createWallet(config)
        try {
            RegtestSupport.mineBlocks(5, wallet.getPrimaryAddress())
            RegtestSupport.mineBlocks(130)
            wallet.sync()
            wallet.createAccount()
            wallet.createSubaddress(1)
            wallet.createSubaddress(1)
            wallet.createSubaddress(0)

            send(wallet, 0, listOf(wallet.getAddress(1, 0) to 1, wallet.getAddress(1, 1) to 2, wallet.getAddress(1, 2) to 3))
            confirm(wallet)
            send(wallet, 0, listOf(wallet.getAddress(0, 1) to 1))
            confirm(wallet)
            send(wallet, 1, listOf(wallet.getAddress(0, 0) to 1))
            confirm(wallet)
            send(wallet, 0, listOf(wallet.getAddress(1, 2) to 1))
            wallet.sync()
            return wallet
        } catch (e: Throwable) {
            wallet.close()
            throw e
        }
    }

    private fun send(wallet: MoneroWalletFull, accountIdx: Int, payments: List<Pair<String, Int>>) {
        val config = MoneroTxConfig()
        config.accountIndex = accountIdx
        config.destinations = payments.map { (address, xmr) -> MoneroDestination(address, BigInteger.valueOf(xmr * 1_000_000_000_000L)) }
        config.relay = true
        wallet.createTxs(config)
    }

    private fun confirm(wallet: MoneroWalletFull) {
        RegtestSupport.mineBlocks(12)
        wallet.sync()
    }

    // what the transfers of a wallet have, like the tests of the transfers of monero-python
    private fun validateTransfer(transfer: MoneroTransfer) {
        val tx = assertNotNull(transfer.tx, "a transfer is in its transaction")
        assertTrue(tx.hash!!.matches(Regex("[0-9a-f]{64}")), "hash of the transaction: ${tx.hash}")
        assertNotNull(transfer.accountIndex)
        assertTrue(transfer.accountIndex!! >= 0)
        assertNotNull(transfer.amount)
        assertTrue(transfer.amount!! >= BigInteger.ZERO)
        assertNotNull(tx.isConfirmed)
        assertNotNull(tx.inTxPool)
        if (tx.isConfirmed == true) {
            assertNotNull(tx.block?.height, "a transaction that is confirmed is in a block")
            assertEquals(false, tx.inTxPool)
        }
        when (transfer) {
            is MoneroIncomingTransfer -> {
                assertTrue(transfer.isIncoming == true)
                assertTrue(transfer.subaddressIndex!! >= 0)
                assertNotNull(transfer.address)
                assertTrue(transfer.address!!.length > 90)
            }
            is MoneroOutgoingTransfer -> {
                assertTrue(transfer.isOutgoing == true)
                assertTrue(transfer.subaddressIndices!!.isNotEmpty())
                assertTrue(transfer.subaddressIndices!!.all { it >= 0 })
                assertNotNull(tx.fee, "a transaction that the wallet sends has a fee")
                assertTrue(tx.isOutgoing == true)
            }
            else -> throw AssertionError("A transfer is incoming or outgoing: $transfer")
        }
    }

    // gets the transfers of a query, which have to be valid, and which the query does not change
    private fun getAndTest(query: MoneroTransferQuery?, expected: Boolean?): List<MoneroTransfer> {
        val copy = query?.copy()
        val transfers = wallet().getTransfers(query)
        when (expected) {
            true -> assertTrue(transfers.isNotEmpty(), "Transfers were expected but not found")
            false -> assertTrue(transfers.isEmpty())
            null -> {}
        }
        transfers.forEach { validateTransfer(it) }
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
