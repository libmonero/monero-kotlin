package monero.wallet

import org.junit.jupiter.api.Tag

/**
 * The keys and the proofs of the transactions of a wallet of a monero-wallet-rpc server, which [BaseMoneroWalletProofsTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcProofsIntegrationTest : BaseMoneroWalletProofsTest() {

    override val wallets: TestWallets = RpcTestWallets

    // the server does not ask for the signature: it checks the empty one, which is not a signature
    override val emptySignatureError = "Signature header check error"
}
