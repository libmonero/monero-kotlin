package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The locked transactions that a full wallet sends, which [BaseMoneroWalletUpdateLockedTest] tests.
 */
@Tag("integration")
class MoneroWalletFullUpdateLockedIntegrationTest : BaseMoneroWalletUpdateLockedTest() {

    override val wallets: TestWallets = FullTestWallets
}
