package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The sync of a full wallet with the transactions of the pool, which [BaseMoneroWalletPoolTest] tests.
 */
@Tag("integration")
class MoneroWalletFullPoolIntegrationTest : BaseMoneroWalletPoolTest() {

    override val wallets: TestWallets = FullTestWallets
}
