package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The transfers of a full wallet, which [BaseMoneroWalletTransfersTest] tests.
 */
@Tag("integration")
class MoneroWalletFullTransfersIntegrationTest : BaseMoneroWalletTransfersTest() {

    override val wallets: TestWallets = FullTestWallets
}
