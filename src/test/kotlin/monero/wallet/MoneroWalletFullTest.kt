package monero.wallet

import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.io.path.exists
import monero.NativeTestSupport
import monero.common.MoneroError
import monero.common.MoneroRpcConnection
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.io.TempDir

/**
 * Mirrors the parts of the full wallet tests of monero-java (TestMoneroWalletFull.java over
 * TestMoneroWalletCommon.java) that run without a daemon: the constructors and the files that they write, the
 * validation of the fields of the create config, and the lifecycle of the wallet through a close and an open. What
 * needs a chain is the integration test of this package, and what is shared with the keys-only wallet is its suite.
 *
 * Every test calls the real dylib through MoneroWalletFull, and skips when it is not found. The wallets are written
 * to a temporary directory, which the JVM removes with its files.
 */
class MoneroWalletFullTest {

    // the test wallet of monero-python's config.ini, which its suites restore from the seed
    private val seed = "vortex degrees outbreak teeming gimmick school rounded tonic observant injury leech ought problems ahead upcoming ledge textbook cigar atrium trash dunes eavesdrop dullness evolved vortex"
    private val primaryAddress = "48W9YHwPzRz9aPTeXCA6kmSpW6HsvmWx578jj3of2gT3JwZzwTf33amESBoNDkL6SVK34Q2HTKqgYbGyE1hBws3wCrcBDR2"
    private val privateViewKey = "e8c2288181bad9ec410d7322efd65f663c6da57bd1d1198636278a039743a600"
    private val privateSpendKey = "be7a2f71097f146bdf0fb5bb8edfe2240a9767e15adee74d95af1b5a64f29a0c"

    // the password that the wallet files of the tests are written with
    private val password = "supersecretpassword123"

    @TempDir
    lateinit var tempDir: Path

    @BeforeTest
    fun requireLibrary() {
        NativeTestSupport.assumeAvailable()
    }

    // ------------------------------- CREATE A WALLET ----------------------------------

    @Test
    fun createWalletRandom_WritesTheWalletAndReadsItsKeys() {
        val path = walletPath()
        assertFalse(MoneroWalletFull.walletExists(path))
        val wallet = MoneroWalletFull.createWalletRandom(config(path))
        try {
            validateMnemonic(wallet.getSeed())
            validateAddress(wallet.getPrimaryAddress())
            validatePrivateKey(wallet.getPrivateViewKey())
            validatePrivateKey(wallet.getPrivateSpendKey())
            assertEquals(MoneroNetworkType.MAINNET, wallet.getNetworkType())
            assertEquals(false, wallet.isViewOnly())
            assertEquals(MoneroWalletFull.DEFAULT_LANGUAGE, wallet.getSeedLanguage())
            assertEquals(path, wallet.getPath())
            assertEquals(1L, wallet.getHeight())
            assertEquals(java.math.BigInteger.ZERO, wallet.getBalance())
            assertEquals(java.math.BigInteger.ZERO, wallet.getUnlockedBalance())
        } finally {
            wallet.close()
        }

        // the wallet was written, so it is still there after the close
        assertTrue(MoneroWalletFull.walletExists(path))
    }

    @Test
    fun createWalletFromSeed_RestoresTheAccountAndTheHeight() {
        val path = walletPath()
        val config = config(path)
        config.seed = seed
        config.restoreHeight = 128
        val wallet = MoneroWalletFull.createWalletFromSeed(config)
        try {
            assertEquals(seed, wallet.getSeed())
            assertEquals(primaryAddress, wallet.getPrimaryAddress())
            assertEquals(privateViewKey, wallet.getPrivateViewKey())
            assertEquals(privateSpendKey, wallet.getPrivateSpendKey())
            assertEquals(MoneroNetworkType.MAINNET, wallet.getNetworkType())
            assertEquals(128L, wallet.getRestoreHeight())
            assertEquals(path, wallet.getPath())
        } finally {
            wallet.close()
        }
        assertTrue(MoneroWalletFull.walletExists(path))
    }

    @Test
    fun createWalletFromSeed_WithABrokenSeed_IsAnError() {
        val config = config(walletPath())
        config.seed = brokenSeed
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWalletFromSeed(config) }
        assertEquals("Invalid mnemonic", error.message)
    }

    @Test
    fun createWalletFromKeys_RestoresTheKeys() {
        val path = walletPath()
        val config = config(path)
        config.primaryAddress = primaryAddress
        config.privateViewKey = privateViewKey
        config.privateSpendKey = privateSpendKey
        val wallet = MoneroWalletFull.createWalletFromKeys(config)
        try {
            assertEquals(primaryAddress, wallet.getPrimaryAddress())
            assertEquals(privateViewKey, wallet.getPrivateViewKey())
            assertEquals(privateSpendKey, wallet.getPrivateSpendKey())
            assertEquals(seed, wallet.getSeed())
            assertEquals(false, wallet.isViewOnly())
        } finally {
            wallet.close()
        }
        assertTrue(MoneroWalletFull.walletExists(path))
    }

    @Test
    fun createWalletFromKeys_WithoutASpendKey_IsViewOnly() {
        val path = walletPath()
        val config = config(path)
        config.primaryAddress = primaryAddress
        config.privateViewKey = privateViewKey
        val wallet = MoneroWalletFull.createWalletFromKeys(config)
        try {
            assertEquals(primaryAddress, wallet.getPrimaryAddress())
            assertEquals(privateViewKey, wallet.getPrivateViewKey())
            assertEquals(true, wallet.isViewOnly())
        } finally {
            wallet.close()
        }

        // a view-only wallet has no seed to read
        val reopened = MoneroWalletFull.openWallet(config(path))
        try {
            val error = assertFailsWith<MoneroError> { reopened.getSeed() }
            assertEquals("The wallet is watch-only. Cannot retrieve seed.", error.message)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun createWalletFromKeys_WithAViewKeyButNoAddress_IsAnError() {
        val config = config(walletPath())
        config.privateViewKey = privateViewKey
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWalletFromKeys(config) }
        assertEquals("must provide primary address if providing private view key", error.message)
    }

    @Test
    fun theAccountsAndSubaddresses_OfTheAccountOfTheSeed() {
        val path = walletPath()
        val config = config(path)
        config.seed = seed
        val wallet = MoneroWalletFull.createWalletFromSeed(config)
        try {
            // account 0 is the primary address, and its subaddress 0 is the primary address too
            assertEquals(primaryAddress, wallet.getAccount(0).primaryAddress)
            assertEquals(primaryAddress, wallet.getSubaddress(0, 0).address)

            // subaddress 1 is an address of its own, which starts with 8 on mainnet
            val subaddress = wallet.getSubaddress(0, 1).address!!
            validateAddress(subaddress)
            assertTrue(subaddress.startsWith("8"), subaddress)
            assertEquals(2, wallet.getSubaddresses(0, listOf(0, 1)).size)
        } finally {
            wallet.close()
        }
    }

    @Test
    fun createWallet_FromAConfigWithoutAPath_StaysInMemory() {
        val config = config()
        config.seed = seed
        val wallet = MoneroWalletFull.createWallet(config)
        try {
            assertEquals(seed, wallet.getSeed())
            assertEquals("", wallet.getPath())
        } finally {
            wallet.close()
        }
    }

    // ------------------------------- THE CREATE CONFIG ----------------------------------

    @Test
    fun createWallet_WithASeedAndKeys_IsAnError() {
        val config = config(walletPath())
        config.seed = seed
        config.primaryAddress = primaryAddress
        config.privateViewKey = privateViewKey
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWallet(config) }
        assertEquals("Wallet may be initialized with a seed or keys but not both", error.message)
    }

    @Test
    fun createWallet_WithARestoreHeightButNoSeedOrKeys_IsAnError() {
        val config = config(walletPath())
        config.restoreHeight = 128
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWallet(config) }
        assertEquals("Cannot specify restore height when creating random wallet", error.message)
    }

    @Test
    fun createWallet_WithRegtestOutsideMainnet_IsAnError() {
        val config = config(walletPath())
        config.networkType = MoneroNetworkType.TESTNET
        config.regtest = true
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWallet(config) }
        assertEquals("Network type must be mainnet when using regtest option", error.message)
    }

    @Test
    fun createWallet_WithoutANetworkType_IsAnError() {
        val config = MoneroWalletConfig()
        config.path = walletPath()
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWallet(config) }
        assertEquals("Must provide wallet network type", error.message)
    }

    @Test
    fun createWalletRandom_WithoutAPath_IsAnError() {
        val config = config()
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWalletRandom(config) }
        assertEquals("Must provide wallet path", error.message)
    }

    @Test
    fun createWalletRandom_AtThePathOfAnotherWallet_IsAnError() {
        val path = walletPath()
        MoneroWalletFull.createWalletRandom(config(path)).use { wallet ->
            assertTrue(MoneroWalletFull.walletExists(path))
        }
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWalletRandom(config(path)) }
        assertEquals("Wallet already exists: $path", error.message)
    }

    // ------------------------------- OPEN A WALLET ----------------------------------

    @Test
    fun openWallet_OfNoWalletAtThePath_IsAnError() {
        val path = walletPath()
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.openWallet(config(path)) }
        assertEquals("file not found \"$path.keys\"", error.message)
    }

    @Test
    fun openWallet_WithTheWrongPassword_IsAnError() {
        val path = walletPath()
        MoneroWalletFull.createWalletRandom(config(path)).use { wallet ->
            assertEquals(path, wallet.getPath())
        }
        val config = config(path)
        config.password = "not the password"
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.openWallet(config) }
        assertTrue(error.message!!.contains("password"), error.message)
    }

    // ------------------------------- THE DAEMON CONNECTION ----------------------------------

    @Test
    fun theDaemonConnection_IsNoneUntilItIsSet() {
        val path = walletPath()
        MoneroWalletFull.createWalletRandom(config(path)).use { wallet ->
            assertEquals(null, wallet.getDaemonConnection())
            assertEquals(false, wallet.isConnectedToDaemon())

            // a wallet syncs with no daemon to sync with is an error
            val sync = assertFailsWith<MoneroError> { wallet.sync() }
            assertEquals("Wallet is not connected to daemon", sync.message)

            // the connection can be set after the wallet is created, and a dead one leaves it unconnected
            wallet.setDaemonConnection(MoneroRpcConnection("http://127.0.0.1:1"))
            assertEquals("http://127.0.0.1:1", wallet.getDaemonConnection()?.uri)
            assertEquals(false, wallet.isConnectedToDaemon())

            // a connection with no uri disconnects the wallet
            wallet.setDaemonConnection(MoneroRpcConnection(""))
            assertEquals(null, wallet.getDaemonConnection())
        }
    }

    // ------------------------------- THE LIFECYCLE ----------------------------------

    @Test
    fun aClosedWallet_RefusesItsCalls() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        assertTrue(wallet.isClosed())
        for (call in listOf(
            { wallet.getSeed() },
            { wallet.getHeight() },
            { wallet.getBalance() },
            { wallet.sync() },
            { wallet.startSyncing(5000) },
            { wallet.stopSyncing() },
            { wallet.setDaemonConnection(MoneroRpcConnection("http://127.0.0.1:18081")) },
            { wallet.save() }
        )) {
            val error = assertFailsWith<MoneroError> { call() }
            assertEquals("Wallet is closed", error.message)
        }

        // closing the wallet again does nothing
        wallet.close()
        assertTrue(wallet.isClosed())
    }

    @Test
    fun closeWithASave_WritesTheWallet() {
        val path = walletPath()
        val config = config(path)
        config.seed = seed
        val wallet = MoneroWalletFull.createWalletFromSeed(config)
        val heightBefore = wallet.getHeight()
        wallet.close(true)
        assertTrue(wallet.isClosed())

        // the wallet opens again with what the save wrote, and the seed is the same account
        val reopened = MoneroWalletFull.openWallet(config)
        try {
            assertEquals(seed, reopened.getSeed())
            assertEquals(primaryAddress, reopened.getPrimaryAddress())
            assertEquals(heightBefore, reopened.getHeight())
        } finally {
            reopened.close()
        }
    }

    @Test
    fun setRestoreHeight_MovesWhereTheWalletScansFrom() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertTrue(wallet.getRestoreHeight() >= 0)
            wallet.setRestoreHeight(10)
            assertEquals(10L, wallet.getRestoreHeight())
            wallet.setRestoreHeight(100)
            assertEquals(100L, wallet.getRestoreHeight())
        }
    }

    @Test
    fun changePassword_WritesTheFilesWithTheNewOne() {
        val path = walletPath()
        val config = config(path)
        config.seed = seed
        MoneroWalletFull.createWalletFromSeed(config).use { wallet ->
            wallet.changePassword(password, "a new password")
        }

        // the wallet opens with the new password, and not with the old one
        val withNew = config(path)
        withNew.password = "a new password"
        val reopened = MoneroWalletFull.openWallet(withNew)
        try {
            assertEquals(seed, reopened.getSeed())
        } finally {
            reopened.close()
        }
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.openWallet(config) }
        assertTrue(error.message!!.contains("password"), error.message)
    }

    @Test
    fun changePassword_WithTheWrongOldOne_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.changePassword("not the password", "a new password") }
            assertEquals("Invalid original password.", error.message)
        }
    }

    // ------------------------------- PRIVATE ------------------------------------

    private fun config(path: String? = null): MoneroWalletConfig {
        val config = MoneroWalletConfig()
        config.path = path
        config.password = password
        config.networkType = MoneroNetworkType.MAINNET
        return config
    }

    private fun walletPath(): String {
        return tempDir.resolve("wallet-${System.nanoTime()}").toString()
    }

    private fun validateMnemonic(seed: String) {
        assertEquals(25, seed.split(" ").size, seed)
    }

    private fun validatePrivateKey(key: String) {
        assertTrue(Regex("[0-9a-f]{64}").matches(key), key)
    }

    private fun validateAddress(address: String) {
        // a standard address of mainnet starts with 4, a subaddress with 8, and both are 95 characters
        assertEquals(95, address.length, address)
        assertTrue(address.startsWith("4") || address.startsWith("8"), address)
    }

    companion object {
        // the seed of monero-python's test_monero_wallet_common.ini, which is two words short of a mnemonic
        private val brokenSeed = "memoir desk algebra inbound innocent unplugs fully okay five inflamed giant factual ritual toyed topic snake unhappy guarded tweezers haunted inundate giant"
    }

    @Test
    fun save_WithoutAPath_IsAnError() {
        val config = MoneroWalletConfig()
        config.networkType = MoneroNetworkType.TESTNET
        val wallet = MoneroWalletFull.createWallet(config)
        try {
            val error = assertFailsWith<MoneroError> { wallet.save() }
            assertEquals("The wallet has no path to write to", error.message)
            val closeError = assertFailsWith<MoneroError> { wallet.close(true) }
            assertEquals("The wallet has no path to write to", closeError.message)
            assertEquals(false, wallet.isClosed())
        } finally {
            wallet.close()
        }
    }
}
