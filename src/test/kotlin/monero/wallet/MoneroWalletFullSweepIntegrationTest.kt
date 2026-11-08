package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The sweeps of a full wallet, which [BaseMoneroWalletSweepTest] tests.
 */
@Tag("integration")
class MoneroWalletFullSweepIntegrationTest : BaseMoneroWalletSweepTest() {

    override val wallets: TestWallets = FullTestWallets
}
