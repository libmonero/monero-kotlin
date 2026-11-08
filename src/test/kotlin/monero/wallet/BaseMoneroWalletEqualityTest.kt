package monero.wallet

import kotlin.test.BeforeTest
import kotlin.test.Test
import monero.RegtestSupport
import monero.daemon.MoneroDaemonRpc

/**
 * A wallet against a ground truth wallet, against a regtest node, like the test of monero-python (tests/test_monero_wallet_common.py) that
 * compares a wallet with a full wallet of the same seed by what the chain says, which every kind of wallet runs: a subclass says which kind.
 * They need monero-c and the regtest node of src/test/docker.
 */
abstract class BaseMoneroWalletEqualityTest {

    /**
     * The kind of wallet that the tests run for.
     */
    protected abstract val wallets: TestWallets

    @BeforeTest
    fun requireLibraryAndNode() {
        wallets.assumeAvailable()
    }

    // Is equal to a ground truth wallet according to on-chain data: a full wallet of the same seed, restored from before the first payment,
    // finds the same accounts, transactions, transfers and outputs. The wallet has no transaction in the pool, since those are not on the chain
    @Test
    fun testWalletEqualityGroundTruth() {
        val restoreHeight = MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { it.getHeight() }
        wallets.create().use { wallet ->
            TxHistoryWallet.populate(wallet, inPool = false)
            FullTestWallets.create(wallet.getSeed(), restoreHeight).use { groundTruth ->
                groundTruth.sync()
                WalletEquality.assertOnChain(groundTruth, wallet)
            }
        }
    }
}
