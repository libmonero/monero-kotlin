package monero.wallet

import java.math.BigInteger
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroError
import monero.daemon.model.MoneroKeyImage
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir

/**
 * The outputs and the key images of a full wallet, against a regtest node, like the tests of monero-python (tests/test_monero_wallet_common.py)
 * that get the outputs with queries, export and import the outputs and the key images, and freeze outputs. A wallet that these tests
 * make is funded with coinbases and pays itself, so it has outputs that are spent and outputs that are not. They need monero-c and the
 * regtest node of src/test/docker.
 */
@Tag("integration")
class MoneroWalletFullOutputsIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    // a wallet with five coinbases that matured, and a payment to itself that was mined, so it has outputs that were spent and ones that were not
    private fun fundedWallet(): MoneroWalletFull {
        val wallet = MoneroWalletFull.createWallet(config())
        try {
            RegtestSupport.mineBlocks(5, wallet.getPrimaryAddress())
            RegtestSupport.mineBlocks(130)
            wallet.sync()
            val txConfig = MoneroTxConfig()
            txConfig.accountIndex = 0
            txConfig.destinations = listOf(MoneroDestination(wallet.getAddress(0, 1), BigInteger.valueOf(3_000_000_000_000L)))
            txConfig.relay = true
            wallet.createTxs(txConfig)
            RegtestSupport.mineBlocks(12)
            wallet.sync()
            return wallet
        } catch (e: Throwable) {
            wallet.close()
            throw e
        }
    }

    // Can get the outputs of the wallet, of an account and of a subaddress
    @Test
    fun testGetOutputs() {
        fundedWallet().use { wallet ->
            val outputs = wallet.getOutputs()
            assertTrue(outputs.isNotEmpty())
            for (output in outputs) {
                assertNotNull(output.keyImage?.hex)
                assertNotNull(output.amount)
                assertNotNull(output.accountIndex)
                assertNotNull(output.subaddressIndex)
                assertNotNull(output.isSpent)
                assertNotNull(output.isFrozen)
                assertNotNull(output.tx)
            }

            val account = MoneroOutputQuery().also { it.accountIndex = 0 }
            assertEquals(outputs.size, wallet.getOutputs(account).size)

            // the outputs of the subaddresses are the ones of the account
            val bySubaddress = wallet.getSubaddresses(0).flatMap { subaddress ->
                val query = MoneroOutputQuery()
                query.accountIndex = 0
                query.subaddressIndex = subaddress.index
                wallet.getOutputs(query).onEach { assertEquals(subaddress.index, it.subaddressIndex) }
            }
            assertEquals(outputs.size, bySubaddress.size)

            val indices = MoneroOutputQuery()
            indices.accountIndex = 0
            indices.subaddressIndices = bySubaddress.map { it.subaddressIndex!! }.distinct()
            assertEquals(outputs.size, wallet.getOutputs(indices).size)
        }
    }

    // Can get the outputs that are spent and the ones that are not, and by amount
    @Test
    fun testGetOutputsWithAQuery() {
        fundedWallet().use { wallet ->
            val unspent = wallet.getOutputs(MoneroOutputQuery().also { it.isSpent = false })
            assertTrue(unspent.isNotEmpty())
            assertTrue(unspent.all { it.isSpent == false })

            val spent = wallet.getOutputs(MoneroOutputQuery().also { it.isSpent = true })
            assertTrue(spent.isNotEmpty(), "the payment of the wallet spent coinbases")
            assertTrue(spent.all { it.isSpent == true })
            assertEquals(wallet.getOutputs().size, spent.size + unspent.size)

            val amount = unspent.first().amount!!
            val bigger = wallet.getOutputs(MoneroOutputQuery().also { it.minAmount = amount })
            assertTrue(bigger.all { it.amount!! >= amount })
            val smaller = wallet.getOutputs(MoneroOutputQuery().also { it.maxAmount = amount })
            assertTrue(smaller.all { it.amount!! <= amount })

            // the outputs of a tx
            val hash = unspent.first().tx!!.hash!!
            val txQuery = MoneroTxQuery().also { it.hash = hash }
            val ofTx = wallet.getOutputs(MoneroOutputQuery().also { it.txQuery = txQuery })
            assertTrue(ofTx.isNotEmpty())
            assertTrue(ofTx.all { it.tx!!.hash == hash })

            // the outputs of some txs, of which one is not a tx
            val byHashes = MoneroTxQuery().also { it.hashes = listOf(hash, "invalid_id") }
            assertEquals(ofTx.size, wallet.getOutputs(MoneroOutputQuery().also { it.txQuery = byHashes }).size)
        }
    }

    // A query by a tx that does not exist has no outputs
    @Test
    fun testGetOutputsOfATxThatIsNotThere() {
        fundedWallet().use { wallet ->
            val query = MoneroOutputQuery().also { it.txQuery = MoneroTxQuery().also { tx -> tx.hash = "invalid_id" } }
            assertTrue(wallet.getOutputs(query).isEmpty())
        }
    }

    // The balances of the wallet are the sums of the ones of its accounts, and of the ones of their subaddresses, and with no transaction in the
    // pool they are the sums of the unspent outputs
    @Test
    fun testAccounting() {
        fundedWallet().use { wallet ->
            // a second account with funds
            wallet.createAccount()
            wallet.createSubaddress(1)
            val payment = MoneroTxConfig()
            payment.accountIndex = 0
            payment.destinations = listOf(MoneroDestination(wallet.getAddress(1, 0), BigInteger.valueOf(2_000_000_000_000L)), MoneroDestination(wallet.getAddress(1, 1), BigInteger.valueOf(1_000_000_000_000L)))
            payment.relay = true
            wallet.createTxs(payment)
            RegtestSupport.mineBlocks(12)
            wallet.sync()
            assertTrue(wallet.getTxs().none { it.inTxPool == true }, "the funded wallet has nothing in the pool")

            val balance = wallet.getBalance()
            val unlockedBalance = wallet.getUnlockedBalance()
            val accounts = wallet.getAccounts(true)
            assertEquals(2, accounts.size)
            assertTrue(accounts[1].balance!!.signum() > 0)
            assertTrue(balance.signum() > 0)
            assertTrue(balance >= unlockedBalance)
            assertEquals(balance, accounts.fold(BigInteger.ZERO) { sum, account -> sum + account.balance!! })
            assertEquals(unlockedBalance, accounts.fold(BigInteger.ZERO) { sum, account -> sum + account.unlockedBalance!! })
            assertEquals(balance, sumOfUnspent(wallet, MoneroOutputQuery()))

            for (account in accounts) {
                assertEquals(account.balance, account.subaddresses!!.fold(BigInteger.ZERO) { sum, subaddress -> sum + subaddress.balance!! })
                assertEquals(account.unlockedBalance, account.subaddresses!!.fold(BigInteger.ZERO) { sum, subaddress -> sum + subaddress.unlockedBalance!! })
                assertEquals(account.balance, sumOfUnspent(wallet, MoneroOutputQuery().also { it.accountIndex = account.index }), "balance of account ${account.index}")
                for (subaddress in account.subaddresses!!) {
                    val query = MoneroOutputQuery().also { it.accountIndex = account.index; it.subaddressIndex = subaddress.index }
                    assertEquals(subaddress.balance, sumOfUnspent(wallet, query), "balance of subaddress ${account.index}:${subaddress.index}")
                }
            }
        }
    }

    private fun sumOfUnspent(wallet: MoneroWalletFull, query: MoneroOutputQuery): BigInteger {
        query.isSpent = false
        return wallet.getOutputs(query).fold(BigInteger.ZERO) { sum, output -> sum + output.amount!! }
    }

    // Can export outputs in hexadecimal
    @Test
    fun testExportOutputs() {
        fundedWallet().use { wallet ->
            val outputsHex = wallet.exportOutputs()
            assertTrue(outputsHex.isNotEmpty())

            // the wallet exports the outputs since the last export by default
            val sinceLast = wallet.exportOutputs()
            val all = wallet.exportOutputs(true)
            assertTrue(all.length > sinceLast.length)
        }
    }

    // Can import outputs in hexadecimal into a wallet that never synced with a node, such as an offline view-only wallet. One that
    // synced is a hot wallet, which does not
    @Test
    fun testImportOutputs() {
        fundedWallet().use { wallet ->
            val outputsHex = wallet.exportOutputs(true)
            assertTrue(outputsHex.isNotEmpty())

            val hot = assertFailsWith<MoneroError> { wallet.importOutputs(outputsHex) }
            assertTrue("Hot wallets cannot import outputs" in hot.message!!, hot.message)

            val config = config()
            config.server = null
            config.regtest = true
            config.primaryAddress = wallet.getPrimaryAddress()
            config.privateViewKey = wallet.getPrivateViewKey()
            config.restoreHeight = 0
            MoneroWalletFull.createWallet(config).use { viewOnly ->
                assertTrue(viewOnly.isViewOnly())
                assertTrue(viewOnly.importOutputs(outputsHex) > 0)
            }
        }
    }

    // Can not import outputs that are not outputs
    @Test
    fun testImportOutputsThatAreNotOutputs() {
        fundedWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.importOutputs("this is not hexadecimal") }
        }
    }

    // Can export signed key images
    @Test
    fun testExportKeyImages() {
        fundedWallet().use { wallet ->
            val result = wallet.exportKeyImages(true)
            assertTrue(result.keyImages!!.isNotEmpty(), "No signed key images in the wallet")
            for (image in result.keyImages!!) {
                assertTrue(image.hex!!.isNotEmpty())
                assertTrue(image.signature!!.isNotEmpty())
            }

            // the wallet exports the key images since the last export by default
            val sinceLast = wallet.exportKeyImages()
            val all = wallet.exportKeyImages(true)
            assertTrue(all.keyImages!!.size > (sinceLast.keyImages?.size ?: 0))
        }
    }

    // Can import key images
    @Test
    fun testImportKeyImages() {
        fundedWallet().use { wallet ->
            val exported = wallet.exportKeyImages(true)
            assertTrue(exported.keyImages!!.isNotEmpty())
            val result = wallet.importKeyImages(exported.keyImages!!)
            assertTrue(result.height!! > 0)
            assertTrue(result.spentAmount!!.signum() >= 0)
            assertTrue(result.unspentAmount!!.signum() >= 0)
            assertTrue(wallet.getBalance().signum() > 0)
            assertTrue(result.spentAmount!!.signum() > 0, "the wallet spent funds")
            assertTrue(result.unspentAmount!!.signum() > 0, "the wallet has funds")
        }
    }

    // Can freeze and thaw an output
    @Test
    fun testFreezeAndThawOutputs() {
        fundedWallet().use { wallet ->
            val available = MoneroOutputQuery().also {
                it.isSpent = false
                it.isFrozen = false
                it.txQuery = MoneroTxQuery().also { tx -> tx.isLocked = false }
            }
            val outputs = wallet.getOutputs(available)
            assertTrue(outputs.isNotEmpty())
            assertTrue(outputs.all { it.isFrozen == false })
            val keyImage = outputs.first().keyImage!!.hex!!
            assertFalse(wallet.isOutputFrozen(keyImage))

            // freeze it
            val frozenQuery = MoneroOutputQuery().also { it.isFrozen = true }
            val frozenBefore = wallet.getOutputs(frozenQuery).size
            wallet.freezeOutput(keyImage)
            assertTrue(wallet.isOutputFrozen(keyImage))
            assertEquals(frozenBefore + 1, wallet.getOutputs(frozenQuery).size)
            val byKeyImage = MoneroOutputQuery()
            byKeyImage.keyImage = MoneroKeyImage().also { it.hex = keyImage }
            byKeyImage.isFrozen = true
            val frozen = wallet.getOutputs(byKeyImage).single()
            assertEquals(true, frozen.isFrozen)
            assertEquals(keyImage, frozen.keyImage!!.hex)

            // thaw it
            wallet.thawOutput(keyImage)
            assertFalse(wallet.isOutputFrozen(keyImage))
            assertEquals(frozenBefore, wallet.getOutputs(frozenQuery).size)
            assertEquals(false, wallet.getOutputs(MoneroOutputQuery().also { it.keyImage = MoneroKeyImage().also { image -> image.hex = keyImage } }).single().isFrozen)
        }
    }

    // A key image that is not one of the wallet's outputs can not be frozen, thawed or checked
    @Test
    fun testFreezeOutputsThatAreNotThere() {
        fundedWallet().use { wallet ->
            val unknown = "a".repeat(64)
            assertFailsWith<MoneroError> { wallet.freezeOutput(unknown) }
            assertFailsWith<MoneroError> { wallet.thawOutput(unknown) }
            assertFailsWith<MoneroError> { wallet.isOutputFrozen(unknown) }
            assertFailsWith<MoneroError> { wallet.freezeOutput("") }
            assertFailsWith<MoneroError> { wallet.thawOutput("") }
            assertFailsWith<MoneroError> { wallet.isOutputFrozen("") }
        }
    }

    private fun config(): MoneroWalletConfig {
        val config = MoneroWalletConfig()
        config.path = tempDir.resolve("wallet-${System.nanoTime()}").toString()
        config.password = "supersecretpassword123"
        config.networkType = MoneroNetworkType.MAINNET
        config.server = RegtestSupport.daemonConnection()
        config.regtest = true
        return config
    }
}
