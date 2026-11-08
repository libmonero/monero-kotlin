package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The scan of transactions and the rescans of a wallet of a monero-wallet-rpc server, which [BaseMoneroWalletScanTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcScanIntegrationTest : BaseMoneroWalletScanTest() {

    override val wallets: TestWallets = RpcTestWallets
}
