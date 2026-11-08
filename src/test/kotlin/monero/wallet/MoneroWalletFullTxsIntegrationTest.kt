package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The transactions of a full wallet, which [BaseMoneroWalletTxsTest] tests.
 */
@Tag("integration")
class MoneroWalletFullTxsIntegrationTest : BaseMoneroWalletTxsTest() {

    override val wallets: TestWallets = FullTestWallets
}
