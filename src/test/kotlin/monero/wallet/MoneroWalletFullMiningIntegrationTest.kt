package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The mining of a full wallet, which [BaseMoneroWalletMiningTest] tests.
 */
@Tag("integration")
class MoneroWalletFullMiningIntegrationTest : BaseMoneroWalletMiningTest() {

    override val wallets: TestWallets = FullTestWallets
}
