package monero.wallet

import monero.RegtestSupport
import org.junit.jupiter.api.Tag

/**
 * The connection of a full wallet to a daemon, which [BaseMoneroWalletConnectionTest] tests.
 */
@Tag("integration")
class MoneroWalletFullConnectionIntegrationTest : BaseMoneroWalletConnectionTest() {

    override val wallets: TestWallets = FullTestWallets

    override val daemonUri: String = RegtestSupport.daemonUri
}
