package monero.wallet

import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroError
import monero.common.MoneroUtils
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.Tag

/**
 * The wallet of a monero-wallet-rpc server, against the server of src/test/docker, like the tests of monero-python
 * (tests/test_monero_wallet_rpc.py) that create wallets on the server, open them and close them. They need monero-c and the server.
 */
@Tag("integration")
class MoneroWalletRpcIntegrationTest {

    private val walletPassword = "supersecretpassword123"

    @BeforeTest
    fun requireLibraryAndServer() {
        RegtestSupport.assumeWalletRpcAvailable()
    }

    private fun newClient() = MoneroWalletRpc(RegtestSupport.walletRpcConnection())

    // the name of a wallet that the server has not
    private fun newName() = "kt-" + UUID.randomUUID().toString().replace("-", "")

    private fun config(name: String = newName()): MoneroWalletConfig {
        val config = MoneroWalletConfig()
        config.path = name
        config.password = walletPassword
        return config
    }

    // Can create a wallet with a randomly generated seed
    @Test
    fun testCreateWalletRandom() {
        // a random wallet with the defaults
        val seed = newClient().use { wallet ->
            wallet.createWallet(config())
            val seed = wallet.getSeed()
            MoneroUtils.validateMnemonic(seed)
            MoneroUtils.validateAddress(wallet.getPrimaryAddress(), MoneroNetworkType.MAINNET)
            // very quick, because the restore height is the chain height
            wallet.sync()
            seed
        }

        // a random wallet in another language, which is another wallet
        newClient().use { wallet ->
            val spanish = config().also { it.language = "Spanish" }
            wallet.createWallet(spanish)
            val spanishSeed = wallet.getSeed()
            MoneroUtils.validateMnemonic(spanishSeed)
            assertNotEquals(seed, spanishSeed)

            // a wallet that exists can not be created again, and the wallet that is open is the one it was
            val error = assertFailsWith<MoneroError> { wallet.createWallet(spanish) }
            assertEquals("Wallet already exists: ${spanish.path}", error.message)
            assertEquals(spanishSeed, wallet.getSeed())
        }
    }

    // Can create a wallet from a seed: the same seed is the same wallet, and another language or an offset makes another one
    @Test
    fun testCreateWalletFromSeed() {
        val (seed, address) = newClient().use { wallet ->
            wallet.createWallet(config())
            wallet.getSeed() to wallet.getPrimaryAddress()
        }

        newClient().use { wallet ->
            val restore = config().also { it.seed = seed; it.restoreHeight = 0 }
            wallet.createWallet(restore)
            assertEquals(seed, wallet.getSeed())
            assertEquals(address, wallet.getPrimaryAddress())
            assertFalse(wallet.isViewOnly())
        }

        // the language and the offset of the seed make a wallet of another seed and another address
        newClient().use { wallet ->
            val other = config().also {
                it.seed = seed
                it.restoreHeight = 0
                it.language = "German"
                it.seedOffset = "my offset!"
            }
            wallet.createWallet(other)
            MoneroUtils.validateMnemonic(wallet.getSeed())
            assertNotEquals(seed, wallet.getSeed())
            assertNotEquals(address, wallet.getPrimaryAddress())
            assertFalse(wallet.isViewOnly())
        }
    }

    // Can open wallets: the wallet that was saved when it was closed, and not one that is not there or with a password that is not its own
    @Test
    fun testOpenWallet() {
        val config = config()
        val seed = newClient().use { wallet ->
            wallet.createWallet(config)
            wallet.getSeed()
        }
        // use() closes without saving: the wallet of the server is on its files since the server created it

        newClient().use { wallet ->
            wallet.openWallet(config)
            assertEquals(seed, wallet.getSeed())
        }
        newClient().use { wallet ->
            wallet.openWallet(config.path!!, walletPassword)
            assertEquals(seed, wallet.getSeed())

            // the server has no wallet of this name
            assertTrue(assertFailsWith<MoneroError> { wallet.openWallet("btc_integrity", walletPassword) }.message!!.startsWith("Failed to open wallet"))
            // the password is not the one of the wallet
            assertEquals("Failed to open wallet : Invalid password.", assertFailsWith<MoneroError> { wallet.openWallet(config.path!!, "not the password") }.message.let { it?.replace("  ", " ") })
        }
    }

    // A wallet that has not opened one has none to answer
    @Test
    fun testWalletWithNoWalletOpen() {
        newClient().use { wallet ->
            assertEquals("No wallet file", assertFailsWith<MoneroError> { wallet.getSeed() }.message)
            assertEquals("No wallet file", assertFailsWith<MoneroError> { wallet.isViewOnly() }.message)
        }
    }

    // Can close a wallet: the wallet is released, and the wallet of the server can be opened by another one
    @Test
    fun testClose() {
        val config = config()
        val wallet = newClient()
        wallet.createWallet(config)
        wallet.sync()
        val seed = wallet.getSeed()

        wallet.close(true)
        assertTrue(wallet.isClosed())
        assertEquals("Wallet is closed", assertFailsWith<MoneroError> { wallet.getHeight() }.message)
        assertEquals("Wallet is closed", assertFailsWith<MoneroError> { wallet.getSeed() }.message)
        assertEquals("Wallet is closed", assertFailsWith<MoneroError> { wallet.sync() }.message)

        newClient().use { reopened ->
            reopened.openWallet(config)
            assertEquals(seed, reopened.getSeed())
            reopened.sync()
        }
    }

    // Can get the languages of the seed that the server writes
    @Test
    fun testGetSeedLanguages() {
        newClient().use { wallet ->
            val languages = wallet.getSeedLanguages()
            assertTrue(languages.isNotEmpty())
            assertTrue("English" in languages && "Spanish" in languages && "German" in languages)
        }
    }

    // Can set the poll period for the notifications of the wallet
    @Test
    fun testSetPollPeriodInMs() {
        newClient().use { wallet ->
            wallet.setPollPeriodInMs(1000)
            wallet.setPollPeriodInMs(10_000)
            assertFailsWith<MoneroError> { wallet.setPollPeriodInMs(0) }
        }
    }

    // Can get the connection to the server
    @Test
    fun testGetRpcConnection() {
        newClient().use { wallet ->
            val connection = wallet.getRpcConnection()
            assertEquals(RegtestSupport.walletRpcUri, connection.uri)
            assertEquals(RegtestSupport.walletRpcUsername, connection.username)
        }
    }
}
