package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The comparison of a full wallet with a ground truth wallet, which [BaseMoneroWalletEqualityTest] tests.
 */
@Tag("integration")
class MoneroWalletFullEqualityIntegrationTest : BaseMoneroWalletEqualityTest() {

    override val wallets: TestWallets = FullTestWallets
}
