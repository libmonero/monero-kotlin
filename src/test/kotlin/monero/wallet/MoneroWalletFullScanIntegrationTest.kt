package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The scan of transactions and the rescans of a full wallet, which [BaseMoneroWalletScanTest] tests.
 */
@Tag("integration")
class MoneroWalletFullScanIntegrationTest : BaseMoneroWalletScanTest() {

    override val wallets: TestWallets = FullTestWallets
}
