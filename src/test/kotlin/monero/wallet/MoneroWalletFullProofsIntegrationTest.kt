package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The keys and the proofs of the transactions of a full wallet, which [BaseMoneroWalletProofsTest] tests.
 */
@Tag("integration")
class MoneroWalletFullProofsIntegrationTest : BaseMoneroWalletProofsTest() {

    override val wallets: TestWallets = FullTestWallets
}
