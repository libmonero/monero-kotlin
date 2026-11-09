package monero.wallet

import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroJson
import monero.daemon.MoneroDaemonRpc
import monero.wallet.TxAssertions.MAX_FEE
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroTxConfig

/**
 * The sync of a wallet with the transactions of the pool, against a regtest node, like the tests of monero-python
 * (tests/test_monero_wallet_common.py) that submit a transaction to the daemon without the wallet, and see whether the wallet knows it after
 * it syncs, which every kind of wallet runs: a subclass says which kind. They need monero-c and the regtest node of src/test/docker.
 *
 * Not ported is test_sync_with_pool_submit_and_flush: it flushes a transaction from the pool, and the wallet takes at least 500 seconds to
 * see that it failed (wallet2::process_unconfirmed_transfer), which monero-python warns about too.
 */
abstract class BaseMoneroWalletPoolTest {

    /**
     * The kind of wallet that the tests run for.
     */
    protected abstract val wallets: TestWallets

    @BeforeTest
    fun requireLibraryAndNode() {
        wallets.assumeAvailable()
    }

    // a wallet with five coinbases that matured. A coinbase can not be mined to a subaddress, so another account is paid by the first
    private fun fundedWallet(accountIndex: Int): MoneroWalletNative {
        val wallet = wallets.create()
        try {
            RegtestSupport.mineBlocks(5, wallet.getPrimaryAddress())
            RegtestSupport.mineBlocks(130)
            wallet.sync()
            if (accountIndex > 0) {
                while (wallet.getAccounts().size <= accountIndex) wallet.createAccount()
                val funding = MoneroTxConfig()
                funding.accountIndex = 0
                // three outputs, so the account has money that is unlocked when a tx of it is in the pool and its change is locked
                val address = wallet.getAddress(accountIndex, 0)
                funding.destinations = List(3) { MoneroDestination(address, BigInteger.valueOf(1_000_000_000_000L)) }
                funding.relay = true
                wallet.createTx(funding)
                RegtestSupport.mineBlocks(12)
                wallet.sync()
                assertTrue(wallet.getUnlockedBalance(accountIndex).signum() > 0, "the account is funded")
            }
            return wallet
        } catch (e: Throwable) {
            wallet.close()
            throw e
        }
    }

    private fun config(wallet: MoneroWalletNative, accountIndex: Int, relay: Boolean? = null): MoneroTxConfig {
        val config = MoneroTxConfig()
        config.accountIndex = accountIndex
        config.address = wallet.getPrimaryAddress()
        config.amount = MAX_FEE.multiply(BigInteger.valueOf(5))
        config.relay = relay
        return config
    }

    // Can sync with txs in the pool sent from and to the same account
    @Test
    fun testSyncWithPoolSameAccounts() {
        fundedWallet(0).use { wallet -> syncWithPoolSubmit(wallet, config(wallet, 0, relay = true)) }
    }

    // Can sync with txs submitted and relayed from the pool
    @Test
    fun testSyncWithPoolSubmitAndRelay() {
        fundedWallet(2).use { wallet -> syncWithPoolSubmit(wallet, config(wallet, 2, relay = true)) }
    }

    // Can sync with txs relayed to the pool: a tx that is relayed to the daemon without the wallet is known to the wallet after it syncs, and the
    // wallet does not make a double spend of it
    @Test
    fun testSyncWithPoolRelay() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            fundedWallet(2).use { wallet ->
                val balanceBefore = wallet.getBalance()
                val unlockedBalanceBefore = wallet.getUnlockedBalance()
                val config = config(wallet, 2)

                // create the tx to relay, and another one with the same config, which would be a double spend
                val tx = wallet.createTx(config)
                val hash = assertNotNull(tx.hash)
                val fee = assertNotNull(tx.fee)
                val txDoubleSpend = wallet.createTx(config)
                assertNotNull(txDoubleSpend.hash)

                // relay the first tx to the daemon, and sync the wallet, which updates from the pool
                assertEquals(true, daemon.submitTxHex(assertNotNull(tx.fullHex)).isGood, "The tx could not be submitted to the pool")
                wallet.sync()

                // the issues are the things that monero-project does not do yet: they are collected and reported at the end
                val issues = mutableListOf<String>()
                assertNotNull(wallet.getTx(hash), "The wallet should know its tx in the pool after it syncs")
                if (unlockedBalanceBefore == wallet.getUnlockedBalance()) {
                    issues.add("The unlocked balance should have decreased after relaying the tx to the daemon")
                }
                if (balanceBefore == wallet.getBalance()) issues.add("The balance should have decreased after relaying the tx to the daemon")
                val expectedBalance = balanceBefore - fee
                if (expectedBalance != wallet.getBalance()) {
                    issues.add("The balance after relaying the tx should be $expectedBalance, and it is ${wallet.getBalance()}")
                }

                // the double spend is refused
                val doubleSpend = daemon.submitTxHex(assertNotNull(txDoubleSpend.fullHex))
                if (doubleSpend.isGood == true) {
                    daemon.flushTxPool(txDoubleSpend.hash!!)
                    issues.add("The result of submitting the double spend should have been a double spend")
                }
                wallet.sync()

                // a tx with the same config is not a double spend once the wallet knows the first
                val tx2 = runCatching { wallet.createTx(config.copy().also { it.relay = true }) }.getOrElse {
                    issues.add("Creating a tx should work after the wallet syncs with the pool, and it fails: ${it.message}")
                    null
                }
                if (tx2 != null) {
                    val result2 = daemon.submitTxHex(assertNotNull(tx2.fullHex), true)
                    if (result2.isDoubleSpend == true) {
                        issues.add("Submitting the tx should work after the wallet syncs with the pool, and it is a double spend")
                    } else {
                        assertEquals(true, result2.isGood)
                    }
                    wallet.sync()
                    if (wallet.getTx(assertNotNull(tx2.hash)) == null) {
                        issues.add("The wallet should know the tx that it made and relayed after it syncs with the pool")
                    }
                    daemon.flushTxPool(tx2.hash!!)
                }

                RegtestSupport.mineBlocks(1)
                assertEquals(emptyList(), issues)
            }
        }
    }

    // A tx that is submitted to the pool with the daemon, without the wallet, is in the pool of the wallet after it syncs, and the daemon refuses
    // the double spend of it. The tx is relayed if the config says so
    private fun syncWithPoolSubmit(wallet: MoneroWalletNative, config: MoneroTxConfig) {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            // rescan spent outputs for a state of the wallet that can be trusted
            wallet.rescanSpent()

            // create the tx without relaying it, and another one with the same config, which is a double spend
            val configNoRelay = config.copy().also { it.relay = false }
            val configNoRelayCopy = configNoRelay.copy()
            val tx = wallet.createTx(configNoRelay)
            val hash = assertNotNull(tx.hash)
            val txDoubleSpend = wallet.createTx(configNoRelay)
            assertNotNull(txDoubleSpend.hash)

            // the config is not changed
            assertEquals(MoneroJson.serialize(configNoRelayCopy), MoneroJson.serialize(configNoRelay))

            try {
                // submit the tx to the pool without relaying it, and sync the wallet, which checks the pool
                val result = daemon.submitTxHex(assertNotNull(tx.fullHex), true)
                assertEquals(true, result.isGood, "The tx could not be submitted to the pool")
                wallet.sync()

                val fetched = assertNotNull(wallet.getTx(hash))
                assertFalse(fetched.isFailed == true, "The submitted tx should not be failed")
                assertEquals(true, fetched.inTxPool, "The submitted tx should be in the pool")

                // the double spend is refused
                val doubleSpend = daemon.submitTxHex(assertNotNull(txDoubleSpend.fullHex), true)
                if (doubleSpend.isGood == true) {
                    daemon.flushTxPool(txDoubleSpend.hash!!)
                    throw AssertionError("The result of submitting the double spend should have been a double spend")
                }

                // relay it if the config says so, and sync the wallet, which updates from the pool
                if (config.relay == true) daemon.relayTxByHash(hash)
                wallet.sync()
            } finally {
                // a block takes the tx, so it is not left in the pool for the tests that come after
                RegtestSupport.mineBlocks(1)
            }
        }
    }
}
