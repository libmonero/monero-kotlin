package monero.wallet

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import monero.NativeTestSupport
import monero.common.MoneroError
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.io.TempDir

/**
 * Mirrors the parts of the full wallet tests of monero-python (tests/test_monero_wallet_full.py over
 * test_monero_wallet_common.py) that run without a daemon: the constructors and the files that they write, the
 * validation of the fields of the create config, and the lifecycle of the wallet through a close and an open. What
 * needs a chain is the integration test of this package, and what the full wallet shares with the keys-only wallet is
 * the suite of that one.
 *
 * Every test calls the real library through MoneroWalletFull, and skips when it is not found. The wallets are written
 * to a temporary directory, which the JVM removes with its files.
 */
class MoneroWalletFullTest {

    // the test wallet of monero-python's config.ini, which its suites restore from the seed
    private val seed = "vortex degrees outbreak teeming gimmick school rounded tonic observant injury leech ought problems ahead upcoming ledge textbook cigar atrium trash dunes eavesdrop dullness evolved vortex"
    private val primaryAddress = "48W9YHwPzRz9aPTeXCA6kmSpW6HsvmWx578jj3of2gT3JwZzwTf33amESBoNDkL6SVK34Q2HTKqgYbGyE1hBws3wCrcBDR2"
    private val privateViewKey = "e8c2288181bad9ec410d7322efd65f663c6da57bd1d1198636278a039743a600"
    private val privateSpendKey = "be7a2f71097f146bdf0fb5bb8edfe2240a9767e15adee74d95af1b5a64f29a0c"

    // the seed of monero-python's test_monero_wallet_common.ini, which is two words short of a mnemonic
    private val brokenSeed = "memoir desk algebra inbound innocent unplugs fully okay five inflamed giant factual ritual toyed topic snake unhappy guarded tweezers haunted inundate giant"

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
    fun createWalletRandom_WritesTheWallet() {
        val path = walletPath()
        assertFalse(MoneroWalletFull.walletExists(path))
        MoneroWalletFull.createWalletRandom(config(path)).use { wallet ->
            assertEquals(path, wallet.getPath())
            assertEquals(MoneroNetworkType.MAINNET, wallet.getNetworkType())
            assertFalse(wallet.isViewOnly())
            assertTrue(wallet.getRestoreHeight() >= 0)
            assertFalse(wallet.isClosed())
        }

        // the wallet was written, so it is still there after the close
        assertTrue(MoneroWalletFull.walletExists(path))
    }

    @Test
    fun createWalletRandom_WithAnotherNetwork_IsOfThatNetwork() {
        val config = config(walletPath())
        config.networkType = MoneroNetworkType.TESTNET
        MoneroWalletFull.createWalletRandom(config).use { wallet ->
            assertEquals(MoneroNetworkType.TESTNET, wallet.getNetworkType())
        }
    }

    @Test
    fun createWalletRandom_WithAnUnknownLanguage_IsAnError() {
        val config = config(walletPath())
        config.language = "Klingon"
        assertFailsWith<MoneroError> { MoneroWalletFull.createWalletRandom(config) }
    }

    @Test
    fun createWalletRandom_WithoutAPath_IsAnError() {
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWalletRandom(config()) }
        assertEquals("Must provide wallet path", error.message)
    }

    @Test
    fun createWalletRandom_WithoutANetworkType_IsAnError() {
        val config = MoneroWalletConfig()
        config.path = walletPath()
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWalletRandom(config) }
        assertEquals("Must provide wallet network type", error.message)
    }

    @Test
    fun createWalletRandom_AtThePathOfAnotherWallet_IsAnError() {
        val path = walletPath()
        MoneroWalletFull.createWalletRandom(config(path)).close()
        assertTrue(MoneroWalletFull.walletExists(path))

        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWalletRandom(config(path)) }
        assertEquals("Wallet already exists: $path", error.message)
    }

    @Test
    fun createWalletFromSeed_KeepsTheRestoreHeight() {
        val path = walletPath()
        val config = config(path)
        config.seed = seed
        config.restoreHeight = 128
        MoneroWalletFull.createWalletFromSeed(config).use { wallet ->
            assertEquals(128L, wallet.getRestoreHeight())
            assertEquals(path, wallet.getPath())
        }
        assertTrue(MoneroWalletFull.walletExists(path))
    }

    @Test
    fun createWalletFromSeed_WithoutARestoreHeight_ScansFromTheGenesis() {
        val config = config(walletPath())
        config.seed = seed
        MoneroWalletFull.createWalletFromSeed(config).use { wallet ->
            assertEquals(0L, wallet.getRestoreHeight())
        }
    }

    @Test
    fun createWalletFromSeed_WithABrokenSeed_IsAnError() {
        val config = config(walletPath())
        config.seed = brokenSeed
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWalletFromSeed(config) }
        assertEquals("Invalid mnemonic", error.message)
    }

    @Test
    fun createWalletFromSeed_WithoutASeed_IsAnError() {
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWalletFromSeed(config(walletPath())) }
        assertEquals("Must provide wallet seed", error.message)
    }

    @Test
    fun createWalletFromKeys_WithAllThreeKeys_IsNotViewOnly() {
        val path = walletPath()
        val config = config(path)
        config.primaryAddress = primaryAddress
        config.privateViewKey = privateViewKey
        config.privateSpendKey = privateSpendKey
        MoneroWalletFull.createWalletFromKeys(config).use { wallet ->
            assertFalse(wallet.isViewOnly())
            assertEquals(path, wallet.getPath())
        }
        assertTrue(MoneroWalletFull.walletExists(path))
    }

    @Test
    fun createWalletFromKeys_WithoutASpendKey_IsViewOnly() {
        val config = config(walletPath())
        config.primaryAddress = primaryAddress
        config.privateViewKey = privateViewKey
        MoneroWalletFull.createWalletFromKeys(config).use { wallet ->
            assertTrue(wallet.isViewOnly())
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
    fun createWalletFromKeys_WithNoKeys_IsAnError() {
        val config = config(walletPath())
        config.restoreHeight = 128
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.createWalletFromKeys(config) }
        assertEquals("Cannot specify restore height when creating random wallet", error.message)
    }

    // ------------------------------- THE CREATE CONFIG ----------------------------------

    @Test
    fun createWallet_FromAConfigWithoutAPath_StaysInMemory() {
        val config = config()
        config.seed = seed
        MoneroWalletFull.createWallet(config).use { wallet ->
            assertEquals("", wallet.getPath())
            assertFalse(wallet.isViewOnly())
        }
    }

    @Test
    fun createWallet_FromAConfigWithAPath_WritesTheWallet() {
        val path = walletPath()
        val config = config(path)
        config.seed = seed
        config.restoreHeight = 128
        MoneroWalletFull.createWallet(config).use { wallet ->
            assertEquals(path, wallet.getPath())
            assertEquals(128L, wallet.getRestoreHeight())
        }
        assertTrue(MoneroWalletFull.walletExists(path))
    }

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

    // ------------------------------- OPEN A WALLET ----------------------------------

    @Test
    fun openWallet_ReadsWhatTheWalletWroteToItsFiles() {
        val path = walletPath()
        val config = config(path)
        config.seed = seed
        config.restoreHeight = 128
        MoneroWalletFull.createWalletFromSeed(config).close()

        MoneroWalletFull.openWallet(config(path)).use { wallet ->
            assertEquals(path, wallet.getPath())
            assertEquals(MoneroNetworkType.MAINNET, wallet.getNetworkType())
            assertFalse(wallet.isViewOnly())
            assertEquals(128L, wallet.getRestoreHeight())
        }
    }

    @Test
    fun openWallet_OfNoWalletAtThePath_IsAnError() {
        val path = walletPath()
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.openWallet(config(path)) }
        assertEquals("file not found \"$path.keys\"", error.message)
    }

    @Test
    fun openWallet_WithTheWrongPassword_IsAnError() {
        val path = walletPath()
        MoneroWalletFull.createWalletRandom(config(path)).close()

        val config = config(path)
        config.password = "not the password"
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.openWallet(config) }
        assertTrue(error.message!!.contains("password"), error.message)
    }

    @Test
    fun openWallet_WithoutAPath_IsAnError() {
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.openWallet(config()) }
        assertEquals("Must provide wallet path", error.message)
    }

    @Test
    fun walletExists_IsFalseForNoWallet() {
        assertFalse(MoneroWalletFull.walletExists(walletPath()))
    }

    // ------------------------------- THE LIFECYCLE ----------------------------------

    @Test
    fun aClosedWallet_RefusesItsCalls() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        assertTrue(wallet.isClosed())
        for (call in listOf(
            { wallet.getPath() },
            { wallet.getNetworkType() },
            { wallet.isViewOnly() },
            { wallet.getVersion() },
            { wallet.getRestoreHeight() },
            { wallet.setRestoreHeight(10) },
            { wallet.changePassword(password, "a new password") },
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
    fun getVersion_ReadsTheLibrary() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val version = wallet.getVersion()
            assertTrue(version.number!! > 0)
            assertEquals(false, version.isRelease == null)
        }
    }

    @Test
    fun setRestoreHeight_MovesWhereTheWalletScansFrom() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            wallet.setRestoreHeight(10)
            assertEquals(10L, wallet.getRestoreHeight())
            wallet.setRestoreHeight(100)
            assertEquals(100L, wallet.getRestoreHeight())
        }
    }

    // the cache file, at the path of the wallet, holds the chain that the wallet has scanned, and save writes it
    @Test
    fun closeWithASave_WritesTheWallet() {
        val path = walletPath()
        val wallet = MoneroWalletFull.createWalletRandom(config(path))
        deleteTheCache(path)

        wallet.close(true)

        assertTrue(wallet.isClosed())
        assertTrue(Files.exists(Path.of(path)), "the close with a save wrote the cache")
    }

    @Test
    fun closeWithoutASave_DoesNotWriteTheWallet() {
        val path = walletPath()
        val wallet = MoneroWalletFull.createWalletRandom(config(path))
        deleteTheCache(path)

        wallet.close(false)

        assertFalse(Files.exists(Path.of(path)), "the close without a save wrote nothing")
    }

    @Test
    fun save_WritesTheWalletBeforeItCloses() {
        val path = walletPath()
        MoneroWalletFull.createWalletRandom(config(path)).use { wallet ->
            deleteTheCache(path)

            wallet.save()

            assertTrue(Files.exists(Path.of(path)), "the save wrote the cache")
        }
    }

    @Test
    fun save_WithoutAPath_IsAnError() {
        val config = MoneroWalletConfig()
        config.networkType = MoneroNetworkType.TESTNET
        MoneroWalletFull.createWallet(config).use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.save() }
            assertEquals("The wallet has no path to write to", error.message)

            // the wallet stays open when a close with a save fails, so it is not lost
            val closeError = assertFailsWith<MoneroError> { wallet.close(true) }
            assertEquals("The wallet has no path to write to", closeError.message)
            assertFalse(wallet.isClosed())
        }
    }

    @Test
    fun changePassword_WritesTheFilesWithTheNewOne() {
        val path = walletPath()
        MoneroWalletFull.createWalletRandom(config(path)).use { wallet ->
            wallet.changePassword(password, "a new password")
        }

        // the wallet opens with the new password, and not with the old one
        val withNew = config(path)
        withNew.password = "a new password"
        MoneroWalletFull.openWallet(withNew).close()
        val error = assertFailsWith<MoneroError> { MoneroWalletFull.openWallet(config(path)) }
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

    private fun deleteTheCache(path: String) {
        assertTrue(Files.deleteIfExists(Path.of(path)), "the wallet has written its cache at $path")
    }

    private fun walletPath(): String {
        return tempDir.resolve("wallet-${System.nanoTime()}").toString()
    }
}
