package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The transactions of a wallet of a monero-wallet-rpc server, which [BaseMoneroWalletTxsTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcTxsIntegrationTest : BaseMoneroWalletTxsTest() {

    override val wallets: TestWallets = RpcTestWallets
}
