package monero.wallet

import monero.RegtestSupport
import org.junit.jupiter.api.Tag

/**
 * The connection of a wallet of a monero-wallet-rpc server to a daemon, which [BaseMoneroWalletConnectionTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcConnectionIntegrationTest : BaseMoneroWalletConnectionTest() {

    override val wallets: TestWallets = RpcTestWallets

    // the server is in the network of the containers, where the node is node_2
    override val daemonUri: String = RegtestSupport.daemonUri.replace("127.0.0.1", "node_2")
}
