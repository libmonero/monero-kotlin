package monero.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import monero.common.MoneroError
import org.junit.jupiter.api.Tag

/**
 * The creation, the relay, the signature and the submission of the transactions of a wallet of a monero-wallet-rpc server, which
 * [BaseMoneroWalletSendTest] tests.
 */
@Tag("integration")
class MoneroWalletRpcSendIntegrationTest : BaseMoneroWalletSendTest() {

    override val wallets: TestWallets = RpcTestWallets

    // the wallet of the server asks for the metadata of a transaction at least, and does not relay nothing
    @Test
    override fun testRelayNoTxs() {
        wallets.create().use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.relayTxs(emptyList<String>()) }
            assertEquals("Must provide an array of tx metadata to relay", error.message)
        }
    }
}
