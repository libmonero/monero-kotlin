package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The outputs and the key images of a wallet of a monero-wallet-rpc server, which [BaseMoneroWalletOutputsTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcOutputsIntegrationTest : BaseMoneroWalletOutputsTest() {

    override val wallets: TestWallets = RpcTestWallets
}
