package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The creation, the relay, the signature and the submission of the transactions of a full wallet, which [BaseMoneroWalletSendTest]
 * tests.
 */
@Tag("integration")
class MoneroWalletFullSendIntegrationTest : BaseMoneroWalletSendTest() {

    override val wallets: TestWallets = FullTestWallets
}
