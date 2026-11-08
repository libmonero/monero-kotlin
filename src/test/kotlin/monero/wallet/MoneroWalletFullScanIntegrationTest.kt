package monero.wallet

import java.math.BigInteger
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir

/**
 * The scan of transactions and the rescans of a full wallet, against a regtest node, like the tests of monero-python
 * (tests/test_monero_wallet_common.py) that scan transactions by their hashes and rescan the spent outputs and the blockchain. They need
 * monero-c and the regtest node of src/test/docker.
 */
@Tag("integration")
class MoneroWalletFullScanIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    private fun newWallet(seed: String? = null, restoreHeight: Long? = null): MoneroWalletFull {
        val config = MoneroWalletConfig()
        config.path = tempDir.resolve("wallet-${System.nanoTime()}").toString()
        config.password = "supersecretpassword123"
        config.networkType = MoneroNetworkType.MAINNET
        config.server = RegtestSupport.daemonConnection()
        config.regtest = true
        config.seed = seed
        config.restoreHeight = restoreHeight
        return MoneroWalletFull.createWallet(config)
    }

    // Can scan transactions by id: monero-python scans two transactions of a wallet with a wallet of the same seed that does not sync, and
    // looks at nothing after, since the transactions that the wallet has after a scan are not the ones that it scanned (its comment: "scanning
    // txs causes merge problems"). Here the transactions are the ones of the wallet and the scan has to succeed and leave the wallet usable
    @Test
    fun testScanTxs() {
        val wallet = TxHistoryWallet.get()
        val hashes = wallet.getTxs().filter { it.isConfirmed == true && it.isMinerTx != true }.take(2).map { it.hash!! }
        assertEquals(2, hashes.size, "Not enough txs to scan")

        newWallet(wallet.getSeed(), 0).use { scan ->
            scan.stopSyncing()
            assertTrue(scan.isConnectedToDaemon())
            scan.scanTxs(hashes)
            assertTrue(scan.getBalance() >= BigInteger.ZERO)
            scan.getTxs()
        }
    }

    // Can rescan spent: the outputs that are spent and the balances are the same after
    @Test
    fun testRescanSpent() {
        val wallet = TxHistoryWallet.get()
        val spent = { wallet.getOutputs().map { it.keyImage!!.hex to it.isSpent } }
        val before = spent()
        val balance = wallet.getBalance()
        val unlocked = wallet.getUnlockedBalance()

        wallet.rescanSpent()
        assertEquals(before, spent())
        assertEquals(balance, wallet.getBalance())
        assertEquals(unlocked, wallet.getUnlockedBalance())
    }

    // Can rescan the blockchain: the wallet has the same transactions and balance after, and not the destinations of what it sent, which the chain
    // does not have. Its key and its note are kept, which monero-c says it loses
    @Test
    fun testRescanBlockchain() {
        newWallet().use { wallet ->
            RegtestSupport.mineBlocks(5, wallet.getPrimaryAddress())
            RegtestSupport.mineBlocks(130)
            wallet.sync()
            val config = MoneroTxConfig()
            config.accountIndex = 0
            config.address = RegtestSupport.MINING_ADDRESS
            config.amount = BigInteger.valueOf(1_000_000_000_000L)
            config.relay = true
            val sent = assertNotNull(wallet.createTx(config).hash)
            RegtestSupport.mineBlocks(1)
            wallet.sync()
            val hashes = wallet.getTxs().map { it.hash }.toSet()
            val balance = wallet.getBalance()
            assertEquals(hashes.size, wallet.getTxs().size)
            assertNotNull(wallet.getTxs(listOf(sent)).single().outgoingTransfer!!.destinations)

            // the rescan syncs the wallet again, so the wallet has its transactions when it returns
            wallet.rescanBlockchain()

            assertEquals(hashes, wallet.getTxs().map { it.hash }.toSet())
            assertEquals(balance, wallet.getBalance())
            // the destinations of the transaction are not in the chain
            assertNull(wallet.getTxs(listOf(sent)).single().outgoingTransfer!!.destinations)
        }
    }
}
