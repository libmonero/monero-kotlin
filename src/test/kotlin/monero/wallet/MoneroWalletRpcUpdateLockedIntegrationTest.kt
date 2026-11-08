package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The locked transactions that a wallet of a monero-wallet-rpc server sends, which [BaseMoneroWalletUpdateLockedTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcUpdateLockedIntegrationTest : BaseMoneroWalletUpdateLockedTest() {

    override val wallets: TestWallets = RpcTestWallets
}
