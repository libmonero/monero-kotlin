package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The comparison of a wallet of a monero-wallet-rpc server with a ground truth wallet, which [BaseMoneroWalletEqualityTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcEqualityIntegrationTest : BaseMoneroWalletEqualityTest() {

    override val wallets: TestWallets = RpcTestWallets
}
