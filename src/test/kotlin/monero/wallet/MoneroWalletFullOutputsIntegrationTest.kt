package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The outputs and the key images of a full wallet, which [BaseMoneroWalletOutputsTest] tests.
 */
@Tag("integration")
class MoneroWalletFullOutputsIntegrationTest : BaseMoneroWalletOutputsTest() {

    override val wallets: TestWallets = FullTestWallets
}
