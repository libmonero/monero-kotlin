package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The transfers of a wallet of a monero-wallet-rpc server, which [BaseMoneroWalletTransfersTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcTransfersIntegrationTest : BaseMoneroWalletTransfersTest() {

    override val wallets: TestWallets = RpcTestWallets
}
