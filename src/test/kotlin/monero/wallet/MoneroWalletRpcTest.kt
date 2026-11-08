package monero.wallet

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import monero.NativeTestSupport
import monero.common.MoneroError
import monero.common.MoneroRpcConnection
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.Tag

/**
 * The wallet of a monero-wallet-rpc server that needs no server, like the tests of monero-python (tests/test_monero_wallet_rpc.py) that
 * connect to a server that is not there. The ones that need the server are in MoneroWalletRpcIntegrationTest. They need monero-c, and skip
 * when it is not found.
 */
@Tag("unit")
class MoneroWalletRpcTest {

    // the server that python gives a wallet which must not reach a server
    private val offlineServerUri = "offline_server_uri"

    @BeforeTest
    fun requireLibrary() {
        NativeTestSupport.assumeAvailable()
    }

    private fun offlineWallet() = MoneroWalletRpc(offlineServerUri, "rpc_user", "abc123")

    // runs the test with a wallet of the server that is not there, which it closes after: the close is a request to the server, which fails
    private fun <T> withWallet(wallet: MoneroWalletRpc = offlineWallet(), test: (MoneroWalletRpc) -> T): T {
        try {
            return test(wallet)
        } finally {
            runCatching { wallet.close() }
        }
    }

    // The wallet connects without asking the server anything: the server is asked by the first call
    @Test
    fun wallet_OfAServerThatIsNotThere_IsMade_AndFailsAtItsFirstCall() {
        val wallet = offlineWallet()
        try {
            val error = assertFailsWith<MoneroError> { wallet.isViewOnly() }
            assertTrue(error.message!!.startsWith("Network error: no response from $offlineServerUri"), error.message)
            assertFalse(wallet.isClosed())
        } finally {
            runCatching { wallet.close() }
        }
    }

    @Test
    fun getRpcConnection_IsTheOneOfTheWallet() {
        withWallet { wallet ->
            val connection = wallet.getRpcConnection()
            assertEquals(offlineServerUri, connection.uri)
            assertEquals("rpc_user", connection.username)
        }
    }

    @Test
    fun wallet_CanBeMadeFromAConnection() {
        withWallet(MoneroWalletRpc(MoneroRpcConnection(offlineServerUri))) { wallet ->
            assertEquals(offlineServerUri, wallet.getRpcConnection().uri)
        }
    }

    // The close of the wallet is a request to the server. When the server does not answer, the error is thrown, and the wallet is closed
    @Test
    fun close_WhenTheServerDoesNotAnswer_ThrowsAndReleasesTheWallet() {
        val wallet = offlineWallet()
        val error = assertFailsWith<MoneroError> { wallet.close() }
        assertTrue(error.message!!.startsWith("Network error"), error.message)
        assertTrue(wallet.isClosed())
        wallet.close()
        assertEquals("Wallet is closed", assertFailsWith<MoneroError> { wallet.isViewOnly() }.message)
    }

    @Test
    fun openAndCreate_WithoutAName_AreAnError() {
        withWallet { wallet ->
            assertEquals("Must provide name of wallet to open", assertFailsWith<MoneroError> { wallet.openWallet(MoneroWalletConfig()) }.message)
            assertEquals("Must provide name of wallet to create", assertFailsWith<MoneroError> { wallet.createWallet(MoneroWalletConfig()) }.message)
        }
    }

    @Test
    fun serverFunctions_OfAClosedWallet_Fail() {
        val wallet = offlineWallet()
        runCatching { wallet.close() }
        val config = MoneroWalletConfig().also { it.path = "name" }
        assertFailsWith<MoneroError> { wallet.openWallet("name", "password") }
        assertFailsWith<MoneroError> { wallet.openWallet(config) }
        assertFailsWith<MoneroError> { wallet.createWallet(config) }
        assertFailsWith<MoneroError> { wallet.getRpcConnection() }
        assertFailsWith<MoneroError> { wallet.getSeedLanguages() }
        assertFailsWith<MoneroError> { wallet.setPollPeriodInMs(1000) }
        assertFailsWith<MoneroError> { wallet.stop() }
    }
}
