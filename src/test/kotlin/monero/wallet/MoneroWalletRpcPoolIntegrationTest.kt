package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The sync of the wallet of a monero-wallet-rpc server with the transactions of the pool, which [BaseMoneroWalletPoolTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcPoolIntegrationTest : BaseMoneroWalletPoolTest() {

    override val wallets: TestWallets = RpcTestWallets
}
