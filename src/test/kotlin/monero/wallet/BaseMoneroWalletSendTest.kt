package monero.wallet

import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroTxWallet

/**
 * The creation, the relay, the signature and the submission of the transactions of a wallet, against a regtest node, like the tests of
 * monero-python (tests/test_monero_wallet_common.py) that send to oneself, to another wallet and to many subaddresses, that create
 * transactions and relay them after, and that sign with an offline wallet what a view-only wallet made, which every kind of wallet runs: a
 * subclass says which kind. The transfer of the wallet between two wallets is in MoneroWalletFullIntegrationTest. They need monero-c and
 * the regtest node of src/test/docker.
 */
abstract class BaseMoneroWalletSendTest {

    /**
     * The kind of wallet that the tests run for.
     */
    protected abstract val wallets: TestWallets

    private val xmr = BigInteger.valueOf(1_000_000_000_000L)

    @BeforeTest
    fun requireLibraryAndNode() {
        wallets.assumeAvailable()
    }

    // a wallet with five coinbases that matured
    private fun fundedWallet(): MoneroWalletNative {
        val wallet = wallets.create()
        try {
            RegtestSupport.mineBlocks(5, wallet.getPrimaryAddress())
            RegtestSupport.mineBlocks(130)
            wallet.sync()
            return wallet
        } catch (e: Throwable) {
            wallet.close()
            throw e
        }
    }

    private fun confirm(vararg toSync: MoneroWalletNative, blocks: Int = 12) {
        RegtestSupport.mineBlocks(blocks)
        toSync.forEach { it.sync() }
    }

    private fun txConfig(address: String, amount: BigInteger, split: Boolean? = null, relay: Boolean? = null): MoneroTxConfig {
        val config = MoneroTxConfig()
        config.accountIndex = 0
        config.address = address
        config.amount = amount
        config.canSplit = split
        config.relay = relay
        return config
    }

    // Can send to oneself, to an integrated address of the wallet
    @Test
    fun testSendToSelf() {
        fundedWallet().use { wallet ->
            val amount = xmr * BigInteger.TWO
            val balance1 = wallet.getBalance()
            val unlocked1 = wallet.getUnlockedBalance()

            // monero-c does not make an integrated address of a subaddress, so there is none to send to
            val subaddress = wallet.createSubaddress(0).address!!
            val refused = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(subaddress, "54491f3bb3572a37") }
            assertEquals("Subaddress shouldn't be used", refused.message)

            val tx = wallet.createTx(txConfig(wallet.getIntegratedAddress().integratedAddress!!, amount, relay = true))
            TxAssertions.validateSent(tx, true)
            assertTrue(wallet.getUnlockedBalance() < unlocked1)
            assertEquals(balance1 - tx.fee!!, wallet.getBalance(), "the balance after is the balance before less the fee")
        }
    }

    // Can send to the address of another wallet, with its payment id, and the other wallet receives what was sent
    @Test
    fun testSendToExternal() {
        fundedWallet().use { wallet ->
            wallets.create().use { recipient ->
                val amount = xmr * BigInteger.TWO
                val balance1 = wallet.getBalance()
                val unlocked1 = wallet.getUnlockedBalance()

                val address = wallet.getIntegratedAddress(recipient.getPrimaryAddress(), "54491f3bb3572a37").integratedAddress!!
                val tx = wallet.createTx(txConfig(address, amount, relay = true))
                TxAssertions.validateSent(tx, true)
                assertTrue(wallet.getUnlockedBalance() < unlocked1)
                assertEquals(balance1 - tx.getOutgoingAmount() - tx.fee!!, wallet.getBalance())
                assertTrue(wallet.getTxs(MoneroTxQuery().also { it.isConfirmed = false }).isNotEmpty())

                confirm(wallet, recipient, blocks = 1)
                assertEquals(amount, recipient.getBalance())
            }
        }
    }

    // Can send to an address in a single transaction or in some, and create it first to relay it after
    @Test
    fun testSendToSingle() {
        fundedWallet().use { wallet ->
            for ((split, relay) in listOf(false to true, true to true, false to false, true to false)) {
                val balance = wallet.getBalance()
                val config = txConfig(wallet.getPrimaryAddress(), xmr, split, relay)
                val copy = MoneroJson.serialize(config)
                val txs = if (split) wallet.createTxs(config) else listOf(wallet.createTx(config))
                assertEquals(copy, MoneroJson.serialize(config), "the config is not changed")
                assertTrue(txs.isNotEmpty())
                if (!split) assertEquals(1, txs.size)
                txs.forEach { TxAssertions.validateSent(it, relay) }

                if (!relay) {
                    assertEquals(balance, wallet.getBalance(), "a transaction that is not relayed moves nothing")
                    val hashes = if (split) wallet.relayTxs(txs.map { it.metadata!! }) else wallet.relayTxs(txs)
                    assertEquals(txs.map { it.hash }, hashes)
                    assertEquals(txs.map { it.hash }.toSet(), wallet.getTxs(MoneroTxQuery().also { it.inTxPool = true }).map { it.hash }.toSet())
                }
                assertTrue(wallet.getBalance() < balance, "the balance decreased by the fee")
                confirm(wallet)
            }
        }
    }

    // Can not relay what is not the metadata of a transaction
    @Test
    fun testRelayWhatIsNotATransaction() {
        fundedWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.relayTx("not the metadata of a transaction") }
            assertFailsWith<MoneroError> { wallet.relayTxs(listOf("not the metadata of a transaction")) }
            assertFailsWith<MoneroError> { wallet.relayTxs(listOf(MoneroTxWallet())) }
        }
    }

    // Relaying no transactions relays none
    @Test
    open fun testRelayNoTxs() {
        wallets.create().use { wallet ->
            assertEquals(emptyList(), wallet.relayTxs(emptyList<String>()))
        }
    }

    // Can not send to an address that is not an address, nor with a payment id on its own
    @Test
    fun testSendToWhatIsNotAnAddressOrWithAPaymentId() {
        fundedWallet().use { wallet ->
            val invalid = assertFailsWith<MoneroError> { wallet.createTx(txConfig("my invalid address", xmr)) }
            assertEquals("Invalid destination address", invalid.message)

            val paymentId = wallet.getIntegratedAddress().paymentId!!
            val config = txConfig(wallet.getPrimaryAddress(), xmr)
            config.paymentId = paymentId + paymentId + paymentId
            val error = assertFailsWith<MoneroError> { wallet.createTx(config) }
            assertEquals("Standalone payment IDs are obsolete. Use subaddresses or integrated addresses instead", error.message)
        }
    }

    // Can send to many subaddresses of many accounts in a transaction or in some, and subtract the fee from the destinations
    @Test
    fun testSendToMultiple() {
        fundedWallet().use { wallet ->
            sendToMultiple(wallet, 5, 3, split = false, subtractFee = false)
            sendToMultiple(wallet, 3, 15, split = true, subtractFee = false)
            sendToMultiple(wallet, 5, 3, split = false, subtractFee = true)
            sendToMultiple(wallet, 3, 15, split = true, subtractFee = true)
        }
    }

    private fun sendToMultiple(wallet: MoneroWalletNative, numAccounts: Int, numSubaddresses: Int, split: Boolean, subtractFee: Boolean) {
        while (wallet.getAccounts().size < numAccounts) wallet.createAccount()
        val addresses = ArrayList<String>()
        for (accountIdx in 0 until numAccounts) {
            while (wallet.getSubaddresses(accountIdx).size < numSubaddresses) wallet.createSubaddress(accountIdx)
            for (subaddressIdx in 0 until numSubaddresses) addresses.add(wallet.getAddress(accountIdx, subaddressIdx))
        }
        val amount = xmr.divide(BigInteger.TEN)
        val sent = amount * BigInteger.valueOf(addresses.size.toLong())

        val config = MoneroTxConfig()
        config.accountIndex = 0
        config.canSplit = split
        config.relay = true
        addresses.forEach { config.addDestination(it, amount) }
        if (subtractFee) config.subtractFeeFrom = addresses.indices.toList()
        val copy = MoneroJson.serialize(config)

        val balance = wallet.getBalance(0)
        val txs = try {
            wallet.createTxs(config)
        } catch (e: MoneroError) {
            // the fee of a transaction can not be subtracted when the transaction is split
            if (subtractFee && split && e.message == "subtractfeefrom transfers cannot be split over multiple transactions yet") return
            throw e
        }
        assertEquals(copy, MoneroJson.serialize(config), "the config is not changed")
        if (!split) assertEquals(1, txs.size)
        assertTrue(wallet.getBalance(0) < balance)

        var fees = BigInteger.ZERO
        var outgoing = BigInteger.ZERO
        for (tx in txs) {
            TxAssertions.validateSent(tx, true)
            fees += tx.fee!!
            outgoing += tx.getOutgoingAmount()
            val destinations = tx.outgoingTransfer!!.destinations!!
            assertTrue(destinations.all { it.address in addresses && it.amount!! > BigInteger.ZERO })
            assertEquals(tx.getOutgoingAmount(), destinations.fold(BigInteger.ZERO) { sum, destination -> sum + destination.amount!! })
        }
        // the amounts sum up to what was sent, less the fees when the fees are subtracted
        assertEquals(if (subtractFee) sent - fees else sent, outgoing)
        confirm(wallet)
    }

    // Can send from many subaddresses of an account in a transaction, which spends from them
    @Test
    fun testSendFromSubaddresses() {
        fundedWallet().use { wallet ->
            wallet.createSubaddress(0)
            wallet.createSubaddress(0)
            val fund = MoneroTxConfig()
            fund.accountIndex = 0
            fund.relay = true
            fund.addDestination(wallet.getAddress(0, 1), xmr * BigInteger.valueOf(3))
            fund.addDestination(wallet.getAddress(0, 2), xmr * BigInteger.valueOf(3))
            wallet.createTx(fund)
            confirm(wallet)
            val balance1 = wallet.getBalance(0, 1)
            val balance2 = wallet.getBalance(0, 2)
            assertEquals(xmr * BigInteger.valueOf(3), balance1)
            assertEquals(xmr * BigInteger.valueOf(3), balance2)

            val spend = txConfig(wallet.getPrimaryAddress(), xmr * BigInteger.valueOf(5), false, true)
            spend.subaddressIndices = listOf(1, 2)
            val tx = wallet.createTx(spend)
            TxAssertions.validateSent(tx, true)
            assertTrue(wallet.getBalance(0, 1) < balance1 && wallet.getBalance(0, 2) < balance2, "the two subaddresses are spent")
        }
    }

    // Can make a transaction with a view-only wallet, sign it with an offline wallet and submit it with the view-only one
    @Test
    fun testViewOnlyAndOfflineWallets() {
        // the wallet that is funded gives its keys and its balance, and is closed before the other two are made: a server has one open
        // wallet, and the wallets of a test are on two servers
        val address: String
        val privateViewKey: String
        val privateSpendKey: String
        val balance: BigInteger
        fundedWallet().use { wallet ->
            address = wallet.getPrimaryAddress()
            privateViewKey = wallet.getPrivateViewKey()
            privateSpendKey = wallet.getPrivateSpendKey()
            balance = wallet.getBalance()
        }

        wallets.createViewOnly(address, privateViewKey).use { viewOnly ->
            wallets.createOffline(address, privateViewKey, privateSpendKey).use { offline ->
                assertTrue(viewOnly.isViewOnly())
                assertEquals(false, offline.isViewOnly())
                assertEquals(false, offline.isConnectedToDaemon())
                viewOnly.sync()
                assertEquals(balance, viewOnly.getBalance())

                // the offline wallet learns the outputs of the view-only one, and gives back their key images
                assertTrue(offline.importOutputs(viewOnly.exportOutputs()) > 0, "No outputs imported")
                viewOnly.importKeyImages(offline.exportKeyImages().keyImages!!)
                assertEquals(balance, viewOnly.getBalance())

                // the view-only wallet creates a transaction that it can not sign
                val unsigned = viewOnly.createTx(txConfig(address, xmr * BigInteger.valueOf(3)))
                val unsignedHex = assertNotNull(unsigned.txSet?.unsignedTxHex)

                // the offline wallet signs it, and describes it
                val signed = offline.signTxs(unsignedHex)
                assertTrue(signed.signedTxHex!!.isNotEmpty())
                assertEquals(1, signed.txs!!.size)
                assertTrue(signed.txs!![0].hash!!.matches(Regex("[0-9a-f]{64}")))

                val described = offline.describeUnsignedTxSet(unsignedHex)
                assertEquals(1, described.txs!!.size)
                val tx = described.txs!![0]
                assertTrue(tx.fee!! > BigInteger.ZERO)
                assertEquals(address, tx.outgoingTransfer!!.destinations!!.first().address)
                assertEquals(xmr * BigInteger.valueOf(3), tx.outgoingTransfer!!.destinations!!.first().amount)
                assertEquals(described.txs!![0].txSet, described)

                // the view-only wallet submits it, and sees it in the pool
                val hashes = viewOnly.submitTxs(signed.signedTxHex!!)
                assertEquals(1, hashes.size)
                assertTrue(hashes[0].matches(Regex("[0-9a-f]{64}")))
                assertEquals(signed.txs!![0].hash, hashes[0])
                viewOnly.sync()
                assertEquals(listOf(hashes[0]), viewOnly.getTxs(MoneroTxQuery().also { it.inTxPool = true }).map { it.hash })
            }
        }
    }

    // Can not sign, describe or submit what is not the hex of transactions
    @Test
    fun testSignDescribeAndSubmitWhatIsNotTransactions() {
        fundedWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.signTxs("not the hex of transactions") }
            assertFailsWith<MoneroError> { wallet.describeUnsignedTxSet("not the hex of transactions") }
            assertFailsWith<MoneroError> { wallet.describeMultisigTxSet("not the hex of transactions") }
            assertFailsWith<MoneroError> { wallet.submitTxs("not the hex of transactions") }
        }
    }
}
