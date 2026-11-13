package monero.wallet

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroRpcConnection

/**
 * The connection of a wallet to a daemon, against a regtest node, like the test of monero-python (tests/test_monero_wallet_common.py) that
 * sets the daemon connection, which every kind of wallet runs: a subclass says which kind. They need monero-c and the regtest node of
 * src/test/docker.
 */
abstract class BaseMoneroWalletConnectionTest {

    /**
     * The kind of wallet that the tests run for.
     */
    protected abstract val wallets: TestWallets

    /**
     * The URI of the node as the wallet reaches it: a wallet of a server is in the network of the containers, where the node has a name of its
     * own, as monero-python's CONTAINER_DAEMON_RPC_URI.
     */
    protected abstract val daemonUri: String

    private val username = RegtestSupport.daemonUsername
    private val password = RegtestSupport.daemonPassword
    private val offlineServerUri = "offline_server_uri"

    @BeforeTest
    fun requireLibraryAndNode() {
        wallets.assumeAvailable()
    }

    private fun assertConnection(wallet: MoneroWallet, uri: String, username: String?, password: String?) {
        val connection = wallet.getDaemonConnection()!!
        assertEquals(uri, connection.uri)
        assertEquals(username ?: "", connection.username ?: "")
        assertEquals(password ?: "", connection.password ?: "")
    }

    // Can set the daemon connection: none, a node that is not there, a node with the credentials that are not its own, and the right ones.
    // monero-python starts from the connection that its wallet was made with, which the wallet of a server does not tell, so this sets it first
    @Test
    fun testSetDaemonConnection() {
        wallets.create().use { wallet ->
            wallet.setDaemonConnection(daemonUri, username, password)
            assertConnection(wallet, daemonUri, username, password)
            assertTrue(wallet.isConnectedToDaemon())

            // an empty URI disconnects the wallet
            wallet.setDaemonConnection("")
            assertNull(wallet.getDaemonConnection())
            assertFalse(wallet.isConnectedToDaemon())

            // a node that is not there
            wallet.setDaemonConnection(offlineServerUri)
            assertConnection(wallet, offlineServerUri, "", "")
            assertFalse(wallet.isConnectedToDaemon())

            // the credentials are not the ones of the node, which asks for a login
            wallet.setDaemonConnection(daemonUri, "wronguser", "wrongpass")
            assertConnection(wallet, daemonUri, "wronguser", "wrongpass")
            assertFalse(wallet.isConnectedToDaemon())

            // the right ones
            wallet.setDaemonConnection(daemonUri, username, password)
            assertConnection(wallet, daemonUri, username, password)
            assertTrue(wallet.isConnectedToDaemon())

            // a connection that is null takes the connection away, and a URI alone sets one without credentials
            wallet.setDaemonConnection(null as MoneroRpcConnection?)
            assertNull(wallet.getDaemonConnection())
            wallet.setDaemonConnection(daemonUri)
            assertConnection(wallet, daemonUri, "", "")
            wallet.setDaemonConnection(null as MoneroRpcConnection?)
            assertNull(wallet.getDaemonConnection())
        }
    }
}
