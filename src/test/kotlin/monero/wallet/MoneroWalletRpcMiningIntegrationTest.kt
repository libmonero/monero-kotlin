package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The mining of a wallet of a monero-wallet-rpc server, which [BaseMoneroWalletMiningTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcMiningIntegrationTest : BaseMoneroWalletMiningTest() {

    override val wallets: TestWallets = RpcTestWallets
}
