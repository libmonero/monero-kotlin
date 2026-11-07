package monero.wallet

import java.math.BigInteger
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
import monero.wallet.TxAssertions.MAX_FEE
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir

/**
 * The sweeps of a full wallet, against a regtest node, like the tests of monero-python (tests/test_monero_wallet_common.py) that sweep
 * outputs by their key images, subaddresses, accounts and the whole wallet, and dust. They need monero-c and the regtest node of
 * src/test/docker.
 */
@Tag("integration")
class MoneroWalletFullSweepIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private val xmr = BigInteger.valueOf(1_000_000_000_000L)

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    // a wallet with five coinbases that matured
    private fun fundedWallet(): MoneroWalletFull {
        val wallet = MoneroWalletFull.createWallet(config())
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

    // a funded wallet that has paid 3 XMR to each of the subaddresses, which are the ones that the pair of its account and its subaddress say
    private fun walletWithFundedSubaddresses(vararg subaddresses: Pair<Int, Int>): MoneroWalletFull {
        val wallet = fundedWallet()
        try {
            for ((accountIdx, subaddressIdx) in subaddresses) {
                while (wallet.getAccounts().size <= accountIdx) wallet.createAccount()
                while (wallet.getSubaddresses(accountIdx).size <= subaddressIdx) wallet.createSubaddress(accountIdx)
            }
            val config = MoneroTxConfig()
            config.accountIndex = 0
            config.relay = true
            subaddresses.forEach { (accountIdx, subaddressIdx) -> config.addDestination(wallet.getAddress(accountIdx, subaddressIdx), xmr * BigInteger.valueOf(3)) }
            wallet.createTx(config)
            confirm(wallet)
            subaddresses.forEach { (accountIdx, subaddressIdx) -> assertEquals(xmr * BigInteger.valueOf(3), wallet.getUnlockedBalance(accountIdx, subaddressIdx)) }
            return wallet
        } catch (e: Throwable) {
            wallet.close()
            throw e
        }
    }

    private fun confirm(wallet: MoneroWalletFull, blocks: Int = 12) {
        RegtestSupport.mineBlocks(blocks)
        wallet.sync()
    }

    private fun sweepConfig(address: String): MoneroTxConfig {
        val config = MoneroTxConfig()
        config.address = address
        config.relay = true
        return config
    }

    // Can sweep individual outputs identified by their key images, which are spent after
    @Test
    fun testSweepOutputs() {
        fundedWallet().use { wallet ->
            val query = MoneroOutputQuery().also {
                it.isSpent = false
                it.txQuery = MoneroTxQuery().also { tx -> tx.isLocked = false }
            }
            val toSweep = wallet.getOutputs(query).filter { it.amount!! > MAX_FEE }.take(3)
            assertEquals(3, toSweep.size, "The wallet does not have enough outputs to sweep")

            for (output in toSweep) {
                assertEquals(false, output.isSpent)
                assertEquals(false, output.tx!!.isLocked)
                val config = sweepConfig(wallet.getAddress(output.accountIndex!!, output.subaddressIndex!!))
                config.keyImage = output.keyImage!!.hex
                val copy = MoneroJson.serialize(config)

                val tx = wallet.sweepOutput(config)
                assertEquals(copy, MoneroJson.serialize(config), "the config is not changed")
                TxAssertions.validateSent(tx, true)
                assertTrue(tx in tx.txSet!!.txs!!)
                assertEquals(config.address, tx.outgoingTransfer!!.destinations!!.single().address)
            }

            // the outputs that were swept are spent, once the sweeps are in a block
            confirm(wallet, 1)
            val after = wallet.getOutputs().filter { output -> toSweep.any { it.keyImage!!.hex == output.keyImage!!.hex } }
            assertEquals(3, after.size)
            assertTrue(after.all { it.isSpent == true }, "Output should be spent")
        }
    }

    // Can not sweep an output that is not one of the wallet, or without saying where
    @Test
    fun testSweepOutputThatCanNotBeSwept() {
        fundedWallet().use { wallet ->
            val unknown = sweepConfig(wallet.getPrimaryAddress()).also { it.keyImage = "a".repeat(64) }
            assertFailsWith<MoneroError> { wallet.sweepOutput(unknown) }

            val output = wallet.getOutputs(MoneroOutputQuery().also { it.isSpent = false }).first()
            val withoutAddress = MoneroTxConfig().also { it.keyImage = output.keyImage!!.hex }
            assertFailsWith<MoneroError> { wallet.sweepOutput(withoutAddress) }
            assertFailsWith<MoneroError> { wallet.sweepOutput(sweepConfig(wallet.getPrimaryAddress())) }
        }
    }

    // Can sweep dust, which is none in a chain with confidential transactions, and relay what it makes if there is any
    @Test
    fun testSweepDust() {
        fundedWallet().use { wallet ->
            val notRelayed = wallet.sweepDust(false)
            if (notRelayed.isNotEmpty()) {
                notRelayed.forEach { TxAssertions.validateSent(it, false) }
                val hashes = wallet.relayTxs(notRelayed.map { it.metadata!! })
                assertEquals(notRelayed.size, hashes.size)
                assertTrue(hashes.all { it.length == 64 })
                confirm(wallet)
            }
            val relayed = wallet.sweepDust(true)
            relayed.forEach { TxAssertions.validateSent(it, true) }
        }
    }

    // Can sweep some subaddresses of an account, which are empty after, and not the others
    @Test
    fun testSweepSubaddresses() {
        walletWithFundedSubaddresses(1 to 0, 1 to 1, 1 to 2).use { wallet ->
            val before = wallet.getAccount(1, true).subaddresses!!.associate { it.index!! to it.unlockedBalance!! }

            for (subaddressIdx in 0..1) {
                val config = sweepConfig(wallet.getPrimaryAddress())
                config.accountIndex = 1
                config.subaddressIndices = listOf(subaddressIdx)
                val txs = wallet.sweepUnlocked(config)

                assertTrue(txs.isNotEmpty())
                for (tx in txs) {
                    TxAssertions.validateSent(tx, true)
                    assertTrue(tx in tx.txSet!!.txs!!)
                    assertEquals(1, tx.outgoingTransfer!!.accountIndex)
                    assertEquals(listOf(subaddressIdx), tx.outgoingTransfer!!.subaddressIndices)
                }
                assertTrue(wallet.getUnlockedBalance(1, subaddressIdx) < MAX_FEE, "the subaddress is swept")
            }

            // the subaddress that was not swept keeps its balance
            val after = wallet.getAccount(1, true).subaddresses!!.associate { it.index!! to it.unlockedBalance!! }
            assertEquals(before[2], after[2])
        }
    }

    // Can sweep an account, which is empty after, and not the other accounts
    @Test
    fun testSweepAccounts() {
        walletWithFundedSubaddresses(1 to 0, 2 to 0).use { wallet ->
            val config = sweepConfig(wallet.getPrimaryAddress())
            config.accountIndex = 1
            val txs = wallet.sweepUnlocked(config)

            assertTrue(txs.isNotEmpty())
            for (tx in txs) {
                TxAssertions.validateSent(tx, true)
                assertTrue(tx in tx.txSet!!.txs!!)
                assertEquals(1, tx.outgoingTransfer!!.accountIndex)
            }
            assertTrue(wallet.getUnlockedBalance(1) < MAX_FEE, "the account is swept")
            assertEquals(xmr * BigInteger.valueOf(3), wallet.getUnlockedBalance(2), "the other account is not")
        }
    }

    // Can sweep the whole wallet by accounts, which leaves nothing that is unlocked
    @Test
    fun testSweepWalletByAccounts() {
        walletWithFundedSubaddresses(1 to 0, 1 to 1, 2 to 0).use { wallet ->
            sweepWallet(wallet, null)
        }
    }

    // Can sweep the whole wallet by subaddresses, which monero-python marks as failing for its wallet RPC server
    @Test
    fun testSweepWalletBySubaddresses() {
        walletWithFundedSubaddresses(1 to 0, 1 to 1, 2 to 0).use { wallet ->
            sweepWallet(wallet, true)
        }
    }

    private fun sweepWallet(wallet: MoneroWalletFull, eachSubaddress: Boolean?) {
        val destination = wallet.getPrimaryAddress()
        val config = sweepConfig(destination)
        config.sweepEachSubaddress = eachSubaddress
        val copy = MoneroJson.serialize(config)
        val txs = wallet.sweepUnlocked(config)
        assertEquals(copy, MoneroJson.serialize(config), "the config is not changed")

        assertTrue(txs.isNotEmpty())
        for (tx in txs) {
            TxAssertions.validateSent(tx, true)
            assertTrue(tx in tx.txSet!!.txs!!)
            assertNull(tx.txSet!!.multisigTxHex)
            assertNull(tx.txSet!!.signedTxHex)
            assertNull(tx.txSet!!.unsignedTxHex)
            assertEquals(destination, tx.outgoingTransfer!!.destinations!!.single().address)
        }

        // no output that is unlocked and not spent is left over the fee, and no subaddress has more unlocked than the fee
        val query = MoneroOutputQuery().also {
            it.isSpent = false
            it.txQuery = MoneroTxQuery().also { tx -> tx.isLocked = false }
        }
        for (output in wallet.getOutputs(query)) assertTrue(output.amount!! < MAX_FEE, "Unspent output should have been swept: ${output.amount}")
        for (account in wallet.getAccounts(true)) {
            for (subaddress in account.subaddresses!!) {
                assertTrue(subaddress.unlockedBalance!! < MAX_FEE, "No subaddress should have more unlocked than the fee")
            }
        }
    }

    // Can not sweep without an address, from an account that is not there, or with nothing unlocked
    @Test
    fun testSweepUnlockedThatCanNotBeSwept() {
        fundedWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.sweepUnlocked(MoneroTxConfig().also { it.relay = true }) }

            val noAccount = sweepConfig(wallet.getPrimaryAddress()).also { it.accountIndex = 99 }
            assertFailsWith<MoneroError> { wallet.sweepUnlocked(noAccount) }

            val nothing = sweepConfig(wallet.getPrimaryAddress()).also { it.accountIndex = 0; it.subaddressIndices = listOf(1) }
            assertFailsWith<MoneroError> { wallet.sweepUnlocked(nothing) }
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
