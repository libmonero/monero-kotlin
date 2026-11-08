package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The sweeps of a wallet of a monero-wallet-rpc server, which [BaseMoneroWalletSweepTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcSweepIntegrationTest : BaseMoneroWalletSweepTest() {

    override val wallets: TestWallets = RpcTestWallets
}
