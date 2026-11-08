package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The notifications of a wallet of a monero-wallet-rpc server, which [BaseMoneroWalletListenersTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcListenersIntegrationTest : BaseMoneroWalletListenersTest() {

    override val wallets: TestWallets = RpcTestWallets

    // The wallet of a server is polled, and monero-cpp asks it for the transactions above a minimum height, which leaves out the ones in the
    // pool: it tells the output when its transaction is in a block, and again when it unlocks
    override val outputStates: List<Pair<Boolean, Boolean>> = listOf(true to true, true to false)
}
