package monero.wallet

import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.common.MoneroError
import monero.common.MoneroRpcConnection
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroAddressBookEntry
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroMessageSignatureType.SIGN_WITH_SPEND_KEY
import monero.wallet.model.MoneroMessageSignatureType.SIGN_WITH_VIEW_KEY
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroTxSet
import monero.wallet.model.MoneroTxWallet
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import monero.wallet.model.MoneroWalletListener

/**
 * Mirrors the parts of the full wallet tests of monero-python (tests/test_monero_wallet_full.py over
 * test_monero_wallet_common.py) that run without a daemon: the constructors and the files that they write, the
 * validation of the fields of the create config, and the lifecycle of the wallet through a close and an open. What
 * needs a chain is the integration test of this package, and what every wallet has to pass, such as the keys, the seed, the addresses
 * and the messages, is in [BaseMoneroWalletTest], which this class extends.
 *
 * Every test calls the real library through MoneroWalletFull, and skips when it is not found. The wallets are written
 * to a temporary directory, which the JVM removes with its files.
 */
@Tag("unit")
class MoneroWalletFullTest : BaseMoneroWalletTest() {

    // the server that python gives a wallet which must not reach a daemon
    private val offlineServerUri = "offline_server_uri"

    // the password that the wallet files of the tests are written with
    private val password = "supersecretpassword123"

    @TempDir
    lateinit var tempDir: Path

    override fun createTestWallet(): MoneroWallet = testWallet()

    override fun createRandomWallet(): MoneroWallet = MoneroWalletFull.createWalletRandom(config(walletPath()))

    override fun closeWallet(wallet: MoneroWallet) = (wallet as MoneroWalletFull).close()

    override fun seedLanguages(wallet: MoneroWallet): List<String> = (wallet as MoneroWalletFull).getSeedLanguages()

    override val defaultLanguage: String = MoneroWalletFull.DEFAULT_LANGUAGE

    // ------------------------------- RING DATABASE ----------------------------------

    // the default directory of the ring database of each network, as test_get_default_ringdb_path_* of monero-python
    @Test
    fun getDefaultRingdbPath_OfEachNetwork() {
        val mainnet = MoneroWalletFull.getDefaultRingdbPath(MoneroNetworkType.MAINNET)
        assertTrue(mainnet.endsWith(".shared-ringdb"), mainnet)
        assertTrue(MoneroWalletFull.getDefaultRingdbPath(MoneroNetworkType.TESTNET).endsWith(".shared-ringdb${java.io.File.separator}testnet"))
        assertTrue(MoneroWalletFull.getDefaultRingdbPath(MoneroNetworkType.STAGENET).endsWith(".shared-ringdb${java.io.File.separator}stagenet"))

        // a regtest wallet is a mainnet wallet
        assertEquals(mainnet, MoneroWalletFull.getDefaultRingdbPath(MoneroNetworkType.REGTEST))
    }

    // ------------------------------- WALLET FILES IN MEMORY ----------------------------------

    // test_export_and_import_wallet_files of monero-python: the wallet opens from the keys buffer, alone and with the cache buffer
    @Test
    fun exportAndImportTheWalletFiles() {
        MoneroWalletFull.createWallet(config()).use { wallet ->
            val keysData = wallet.getKeysFileBuffer(password, false)
            val cacheData = wallet.getCacheFileBuffer()
            assertTrue(keysData.isNotEmpty())
            assertTrue(cacheData.isNotEmpty())

            // the cache can be left out, as null and as an empty buffer
            val withKeysOnly = MoneroWalletFull.openWalletData(password, MoneroNetworkType.MAINNET, keysData)
            val withEmptyCache = MoneroWalletFull.openWalletData(password, MoneroNetworkType.MAINNET, keysData, ByteArray(0))
            val withBoth = MoneroWalletFull.openWalletData(password, MoneroNetworkType.MAINNET, keysData, cacheData)
            for (restored in listOf(withKeysOnly, withEmptyCache, withBoth)) {
                restored.use {
                    assertEquals(wallet.getSeed(), it.getSeed())
                    assertEquals(wallet.getPrimaryAddress(), it.getPrimaryAddress())
                    assertEquals(wallet.getPrivateViewKey(), it.getPrivateViewKey())
                    assertEquals(wallet.getPrivateSpendKey(), it.getPrivateSpendKey())
                    assertFalse(it.isViewOnly())
                }
            }
        }
    }

    @Test
    fun exportTheKeysOfAViewOnlyWallet_LeavesTheSpendKeyOut() {
        MoneroWalletFull.createWallet(config()).use { wallet ->
            val keysData = wallet.getKeysFileBuffer(password, true)
            MoneroWalletFull.openWalletData(password, MoneroNetworkType.MAINNET, keysData).use { viewOnly ->
                assertTrue(viewOnly.isViewOnly())
                assertEquals(wallet.getPrimaryAddress(), viewOnly.getPrimaryAddress())
                assertEquals(wallet.getPrivateViewKey(), viewOnly.getPrivateViewKey())
            }
        }
    }

    @Test
    fun exportTheKeys_WithAnotherPassword_IsAnError() {
        MoneroWalletFull.createWallet(config()).use { wallet ->
            assertFailsWith<MoneroError> { wallet.getKeysFileBuffer("not the password", false) }
        }
    }

    @Test
    fun importTheKeys_WithAnotherPassword_OrAnEmptyBuffer_IsAnError() {
        MoneroWalletFull.createWallet(config()).use { wallet ->
            val keysData = wallet.getKeysFileBuffer(password, false)
            assertFailsWith<MoneroError> { MoneroWalletFull.openWalletData("not the password", MoneroNetworkType.MAINNET, keysData) }
            assertFailsWith<MoneroError> { MoneroWalletFull.openWalletData(password, MoneroNetworkType.MAINNET, ByteArray(0)) }
            assertFailsWith<MoneroError> { MoneroWalletFull.openWalletData(password, MoneroNetworkType.MAINNET, "not a keys file".toByteArray()) }
        }
    }

    @Test
    fun exportTheWalletFiles_OfAClosedWallet_IsAnError() {
        val wallet = MoneroWalletFull.createWallet(config())
        wallet.close()
        assertFailsWith<MoneroError> { wallet.getKeysFileBuffer(password, false) }
        assertFailsWith<MoneroError> { wallet.getCacheFileBuffer() }
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

    // ------------------------------- SEED AND KEYS ----------------------------------

    @Test
    fun getSeedLanguage_IsTheLanguageOfTheConfig() {
        val config = config()
        config.language = "Spanish"
        MoneroWalletFull.createWallet(config).use { wallet ->
            assertEquals("Spanish", wallet.getSeedLanguage())
        }
    }

    @Test
    fun getSeedLanguages_NeedsNoWallet() {
        val wallet = testWallet()
        wallet.close()
        assertTrue(wallet.getSeedLanguages().contains(MoneroWalletFull.DEFAULT_LANGUAGE))
    }

    @Test
    fun theKeys_OfAWalletFromKeys_AreTheOnesOfItsSeed() {
        val config = config(walletPath())
        config.primaryAddress = primaryAddress
        config.privateViewKey = privateViewKey
        config.privateSpendKey = privateSpendKey
        MoneroWalletFull.createWalletFromKeys(config).use { wallet ->
            assertEquals(seed, wallet.getSeed())
            assertEquals(primaryAddress, wallet.getPrimaryAddress())
            assertEquals(privateViewKey, wallet.getPrivateViewKey())
            assertEquals(publicViewKey, wallet.getPublicViewKey())
            assertEquals(privateSpendKey, wallet.getPrivateSpendKey())
            assertEquals(publicSpendKey, wallet.getPublicSpendKey())
        }
    }

    @Test
    fun aViewOnlyWallet_HasNoSeedAndNoSpendKey() {
        val path = walletPath()
        val config = config(path)
        config.primaryAddress = primaryAddress
        config.privateViewKey = privateViewKey
        MoneroWalletFull.createWalletFromKeys(config).close()

        MoneroWalletFull.openWallet(config(path)).use { wallet ->
            assertEquals(privateViewKey, wallet.getPrivateViewKey())
            assertEquals(publicViewKey, wallet.getPublicViewKey())
            assertEquals(publicSpendKey, wallet.getPublicSpendKey())
            val seedError = assertFailsWith<MoneroError> { wallet.getSeed() }
            assertEquals("The wallet is watch-only. Cannot retrieve seed.", seedError.message)
            assertFailsWith<MoneroError> { wallet.getPrivateSpendKey() }
        }
    }

    // ------------------------------- ADDRESSES, ACCOUNTS AND SUBADDRESSES ----------------------------------

    @Test
    fun getAddress_OfASubaddress_IsAnAddressOfItsOwn() {
        testWallet().use { wallet ->
            val subaddress = wallet.getAddress(0, 1)
            validateAddress(subaddress)

            // a subaddress of mainnet starts with 8
            assertTrue(subaddress.startsWith("8"), subaddress)
            assertEquals(subaddress, wallet.getAddress(0, 1))
            assertTrue(subaddress != wallet.getAddress(1, 1))
        }
    }

    @Test
    fun getAccount_ReadsTheFirstAccount() {
        testWallet().use { wallet ->
            val account = wallet.getAccount(0)
            assertEquals(0, account.index)
            assertEquals(primaryAddress, account.primaryAddress)
            assertTrue(account.subaddresses.isNullOrEmpty(), account.subaddresses.toString())
        }
    }

    @Test
    fun getAccount_WithSubaddresses_ReadsThem() {
        testWallet().use { wallet ->
            val account = wallet.getAccount(0, true)
            assertEquals(primaryAddress, account.primaryAddress)
            val subaddresses = account.subaddresses!!
            assertTrue(subaddresses.isNotEmpty())
            assertEquals(primaryAddress, subaddresses[0].address)
            for ((i, subaddress) in subaddresses.withIndex()) {
                assertEquals(0, subaddress.accountIndex)
                assertEquals(i, subaddress.index)
                assertEquals(wallet.getAddress(0, i), subaddress.address)
            }
        }
    }

    @Test
    fun getSubaddresses_WithoutIndices_ReadsAllOfThem() {
        testWallet().use { wallet ->
            val all = wallet.getSubaddresses(0)
            assertTrue(all.isNotEmpty())
            assertEquals(primaryAddress, all[0].address)
            assertEquals(wallet.getAccount(0, true).subaddresses!!.map { it.address }, all.map { it.address })
            assertEquals(all.map { it.address }, wallet.getSubaddresses(0, emptyList()).map { it.address })
        }
    }

    @Test
    fun getSubaddresses_ReadsTheIndices() {
        testWallet().use { wallet ->
            val subaddresses = wallet.getSubaddresses(0, listOf(0, 0))
            assertEquals(2, subaddresses.size)
            for (subaddress in subaddresses) {
                assertEquals(0, subaddress.accountIndex)
                assertEquals(0, subaddress.index)
                assertEquals(primaryAddress, subaddress.address)
            }
        }
    }

    @Test
    fun getSubaddress_MatchesThePluralCall() {
        testWallet().use { wallet ->
            val subaddress = wallet.getSubaddress(0, 0)
            assertEquals(primaryAddress, subaddress.address)
            assertEquals(0, subaddress.accountIndex)
            assertEquals(0, subaddress.index)
            assertEquals(subaddress.address, wallet.getSubaddresses(0, listOf(0))[0].address)
        }
    }

    // ------------------------------- INTEGRATED ADDRESSES ----------------------------------

    // ------------------------------- MESSAGES ----------------------------------

    @Test
    fun signAndVerifyMessages_WithTheSpendKeyAndTheViewKey() {
        val message = "This is a super important message which needs to be signed and verified."
        testWallet().use { wallet ->
            for (signatureType in listOf(SIGN_WITH_SPEND_KEY, SIGN_WITH_VIEW_KEY)) {
                for ((accountIdx, subaddressIdx) in listOf(Pair(0, 0), Pair(0, 1), Pair(1, 0))) {
                    val address = wallet.getAddress(accountIdx, subaddressIdx)
                    val signature = wallet.signMessage(message, signatureType, accountIdx, subaddressIdx)

                    val result = wallet.verifyMessage(message, address, signature)
                    assertEquals(true, result.isGood)
                    assertEquals(signatureType, result.signatureType)
                    assertEquals(false, wallet.verifyMessage("a different message", address, signature).isGood)
                }
            }
        }
    }

    @Test
    fun verifyMessage_OfAnotherAddress_IsNotGood() {
        testWallet().use { wallet ->
            val signature = wallet.signMessage("hello", SIGN_WITH_SPEND_KEY, 0, 0)
            assertEquals(false, wallet.verifyMessage("hello", wallet.getAddress(0, 1), signature).isGood)
        }
    }

    // ------------------------------- PAYMENT URIS ----------------------------------

    @Test
    fun getPaymentUri_WithoutAnAddress_IsAnError() {
        testWallet().use { wallet ->
            val config = MoneroTxConfig()
            config.destinations = listOf(MoneroDestination(null, BigInteger.ZERO))
            val error = assertFailsWith<MoneroError> { wallet.getPaymentUri(config) }
            assertTrue(error.message!!.contains("Cannot make URI from supplied parameters"), error.message)
        }
    }

    @Test
    fun parsePaymentUri_OfNoUri_IsAnError() {
        testWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.parsePaymentUri("this is not a uri") }
        }
    }

    // ------------------------------- THE DAEMON CONNECTION ----------------------------------

    @Test
    fun theDaemonConnection_IsNoneUntilItIsSet() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertNull(wallet.getDaemonConnection())

            wallet.setDaemonConnection(MoneroRpcConnection(offlineServerUri))
            assertEquals(offlineServerUri, wallet.getDaemonConnection()?.uri)
        }
    }

    @Test
    fun setDaemonConnection_WithNoUriOrNull_DisconnectsTheWallet() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            wallet.setDaemonConnection(MoneroRpcConnection(offlineServerUri))
            assertNotNull(wallet.getDaemonConnection())

            wallet.setDaemonConnection(MoneroRpcConnection(""))
            assertNull(wallet.getDaemonConnection())

            wallet.setDaemonConnection(MoneroRpcConnection(offlineServerUri))
            wallet.setDaemonConnection(null)
            assertNull(wallet.getDaemonConnection())
        }
    }

    @Test
    fun setDaemonConnection_KeepsTheCredentials() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val connection = MoneroRpcConnection(offlineServerUri)
            connection.username = "superuser"
            connection.password = "abctesting123"
            wallet.setDaemonConnection(connection)

            val read = wallet.getDaemonConnection()!!
            assertEquals(offlineServerUri, read.uri)
            assertEquals("superuser", read.username)
            assertEquals("abctesting123", read.password)
        }
    }

    @Test
    fun theDaemonConnection_IsNotKeptInTheFiles() {
        val path = walletPath()
        MoneroWalletFull.createWalletRandom(config(path)).use { wallet ->
            wallet.setDaemonConnection(MoneroRpcConnection(offlineServerUri))
            wallet.save()
        }

        MoneroWalletFull.openWallet(config(path)).use { wallet ->
            assertNull(wallet.getDaemonConnection())
        }
    }

    @Test
    fun createWallet_WithAServer_ConnectsTheWallet() {
        val config = config()
        config.server = MoneroRpcConnection(offlineServerUri)
        MoneroWalletFull.createWallet(config).use { wallet ->
            assertEquals(offlineServerUri, wallet.getDaemonConnection()?.uri)
        }
    }

    @Test
    fun theTrustOfTheDaemon_IsWhatTheConfigAndTheConnectionSay() {
        val config = config()
        config.server = MoneroRpcConnection(offlineServerUri)
        config.isTrustedDaemon = true
        MoneroWalletFull.createWallet(config).use { wallet ->
            assertTrue(wallet.isDaemonTrusted())

            wallet.setDaemonConnection(MoneroRpcConnection(offlineServerUri), false)
            assertFalse(wallet.isDaemonTrusted())

            wallet.setDaemonConnection(MoneroRpcConnection(offlineServerUri), true)
            assertTrue(wallet.isDaemonTrusted())
        }
    }

    @Test
    fun aDaemonOnALocalAddress_IsTrustedWhenTheTrustIsNotSet() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            wallet.setDaemonConnection(MoneroRpcConnection("http://127.0.0.1:18081"))
            assertTrue(wallet.isDaemonTrusted())

            wallet.setDaemonConnection(MoneroRpcConnection("http://daemon.example.org:18081"))
            assertFalse(wallet.isDaemonTrusted())
        }
    }

    // ------------------------------- SYNC AND THE CHAIN ----------------------------------

    @Test
    fun aNewWallet_HasNotScanned() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertFalse(wallet.isConnectedToDaemon())
            assertFalse(wallet.isSynced())

            // wallet2 starts the height of a wallet that has not scanned at 1
            assertEquals(1L, wallet.getHeight())
            assertEquals(BigInteger.ZERO, wallet.getBalance())
            assertEquals(BigInteger.ZERO, wallet.getUnlockedBalance())
        }
    }

    @Test
    fun aWalletWithAnOfflineServer_IsNotConnected() {
        val config = config()
        config.server = MoneroRpcConnection(offlineServerUri)
        MoneroWalletFull.createWallet(config).use { wallet ->
            assertFalse(wallet.isConnectedToDaemon())
            assertFalse(wallet.isSynced())
        }
    }

    @Test
    fun theCallsThatAskTheDaemon_OfAWalletThatIsNotConnected_AreAnError() {
        val config = config()
        config.server = MoneroRpcConnection(offlineServerUri)
        MoneroWalletFull.createWallet(config).use { wallet ->
            // wallet2 masks the errors of a daemon that it does not trust
            assertEquals("daemon error", assertFailsWith<MoneroError> { wallet.getDaemonHeight() }.message)
            assertEquals("daemon error", assertFailsWith<MoneroError> { wallet.isDaemonSynced() }.message)
            assertEquals("daemon error", assertFailsWith<MoneroError> { wallet.getDaemonMaxPeerHeight() }.message)
            assertEquals("failed to connect to daemon: $offlineServerUri", assertFailsWith<MoneroError> { wallet.getHeightByDate(2026, 1, 1) }.message)
        }
    }

    // A date that does not fit the function of monero-c is refused, where it would be cut to fit and mean another date
    @Test
    fun getHeightByDate_OfADateThatIsNotValid_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            for ((year, month, day) in listOf(Triple(2026, 0, 1), Triple(2026, 13, 1), Triple(2026, 1, 0), Triple(2026, 1, 32), Triple(-1, 1, 1), Triple(65536, 1, 1))) {
                assertEquals("Invalid date: $year-$month-$day", assertFailsWith<MoneroError> { wallet.getHeightByDate(year, month, day) }.message)
            }
        }
    }

    @Test
    fun sync_OfAWalletThatIsNotConnected_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.sync() }
            assertEquals("Wallet is not connected to daemon", error.message)
        }
    }

    @Test
    fun startSyncing_SucceedsWhileTheWalletIsNotConnected() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            wallet.startSyncing(1000)
            wallet.stopSyncing()
        }
    }

    @Test
    fun startSyncing_WithAPeriodOutOfRange_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertFailsWith<MoneroError> { wallet.startSyncing(0) }
        }
    }

    @Test
    fun waitForNextBlock_OfAWalletThatIsNotConnected_TimesOut() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertNull(wallet.waitForNextBlock(100))
        }
    }

    @Test
    fun waitForNextBlock_WithATimeoutOutOfRange_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertFailsWith<MoneroError> { wallet.waitForNextBlock(0) }
        }
    }

    // ------------------------------- THE SHUTDOWN ----------------------------------

    @Test
    fun requestShutdown_OfAWalletThatIsNotSyncing_Succeeds() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            wallet.requestShutdown()
            wallet.requestShutdown()
        }
    }

    @Test
    fun requestShutdown_OfAClosedWallet_IsAnError() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        val error = assertFailsWith<MoneroError> { wallet.requestShutdown() }
        assertEquals("Wallet is closed", error.message)
    }

    // The call of another thread never reaches a handle that the close has freed: it ends with the error of a closed
    // wallet, and the process stays up
    @Test
    fun requestShutdown_WhileTheWalletCloses_NeverReachesAFreedHandle() {
        repeat(20) {
            val wallet = testWallet()
            val ended = CompletableFuture<Throwable?>()
            val caller = Thread {
                try {
                    while (true) wallet.requestShutdown()
                } catch (e: Throwable) {
                    ended.complete(e)
                }
            }.apply { isDaemon = true; start() }
            Thread.sleep(5)

            wallet.close()

            val error = ended.get(10, TimeUnit.SECONDS)
            assertTrue(error is MoneroError, "the caller ends with the error of a closed wallet, not $error")
            assertEquals("Wallet is closed", error.message)
            caller.join(1000)
        }
    }

    // ------------------------------- TRANSACTIONS ----------------------------------

    @Test
    fun getTxs_OfAWalletThatIsNotConnected_IsAnError() {
        testWallet().use { wallet ->
            assertEquals("no connection to daemon", assertFailsWith<MoneroError> { wallet.getTxs() }.message)
            assertEquals("no connection to daemon", assertFailsWith<MoneroError> { wallet.getTxs(MoneroTxQuery()) }.message)
        }
    }

    @Test
    fun createTxs_OfAWalletThatIsNotConnected_IsAnError() {
        testWallet().use { wallet ->
            val config = MoneroTxConfig()
            config.accountIndex = 0
            config.destinations = listOf(MoneroDestination(primaryAddress, BigInteger.valueOf(1000000000000L)))
            config.relay = false
            val error = assertFailsWith<MoneroError> { wallet.createTxs(config) }
            assertEquals("Failed to get height", error.message)
        }
    }

    @Test
    fun createTxs_WithoutAnAccount_IsAnError() {
        testWallet().use { wallet ->
            val config = MoneroTxConfig()
            config.destinations = listOf(MoneroDestination(primaryAddress, BigInteger.valueOf(1000000000000L)))
            val error = assertFailsWith<MoneroError> { wallet.createTxs(config) }
            assertEquals("Must specify account index to send from", error.message)
        }
    }

    @Test
    fun createTxs_WithoutADestination_IsAnError() {
        testWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.createTxs(MoneroTxConfig()) }
        }
    }

    @Test
    fun relayTx_OfMetadataThatIsNotOne_IsAnError() {
        testWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.relayTx("this is not the metadata of a transaction") }
        }
    }

    @Test
    fun relayTx_OfATxWithoutMetadata_IsAnError() {
        testWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.relayTx(MoneroTxWallet()) }
        }
    }

    @Test
    fun createTx_OfAWalletThatIsNotConnected_IsAnError() {
        testWallet().use { wallet ->
            val config = MoneroTxConfig()
            config.accountIndex = 0
            config.destinations = listOf(MoneroDestination(primaryAddress, BigInteger.valueOf(1000000000000L)))
            assertEquals("Failed to get height", assertFailsWith<MoneroError> { wallet.createTx(config) }.message)
        }
    }

    @Test
    fun createTx_WithoutAnAccountOrADestination_IsAnError() {
        testWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.createTx(MoneroTxConfig()) }
            val config = MoneroTxConfig()
            config.destinations = listOf(MoneroDestination(primaryAddress, BigInteger.valueOf(1000000000000L)))
            assertEquals("Must specify account index to send from", assertFailsWith<MoneroError> { wallet.createTx(config) }.message)
        }
    }

    @Test
    fun relayTxs_OfWhatAreNotTxs_AreAnError() {
        testWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.relayTxs(listOf("this is not the metadata of a transaction")) }
            assertFailsWith<MoneroError> { wallet.relayTxs(listOf(MoneroTxWallet())) }
        }
    }

    @Test
    fun signDescribeAndSubmitTxs_OfWhatIsNotTheHexOfTxs_AreAnError() {
        testWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.signTxs("this is not the hex of transactions") }
            assertFailsWith<MoneroError> { wallet.describeUnsignedTxSet("this is not the hex of transactions") }
            assertFailsWith<MoneroError> { wallet.describeMultisigTxSet("this is not the hex of transactions") }
            assertFailsWith<MoneroError> { wallet.describeTxSet(MoneroTxSet()) }
            assertFailsWith<MoneroError> { wallet.submitTxs("this is not the hex of transactions") }
        }
    }

    @Test
    fun txsFunctions_OfAClosedWallet_Fail() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        val txConfig = MoneroTxConfig()
        assertFailsWith<MoneroError> { wallet.createTx(txConfig) }
        assertFailsWith<MoneroError> { wallet.relayTxs(listOf("")) }
        assertFailsWith<MoneroError> { wallet.relayTxs(listOf(MoneroTxWallet())) }
        assertFailsWith<MoneroError> { wallet.describeTxSet(MoneroTxSet()) }
        assertFailsWith<MoneroError> { wallet.signTxs("") }
        assertFailsWith<MoneroError> { wallet.submitTxs("") }
    }

    @Test
    fun sweeps_OfAWalletThatIsNotConnected_AreAnError() {
        testWallet().use { wallet ->
            val config = MoneroTxConfig()
            config.address = primaryAddress
            config.accountIndex = 0
            assertFailsWith<MoneroError> { wallet.sweepUnlocked(config) }
            assertFailsWith<MoneroError> { wallet.sweepDust(false) }
            config.keyImage = "a".repeat(64)
            assertFailsWith<MoneroError> { wallet.sweepOutput(config) }
        }
    }

    @Test
    fun sweeps_WithoutAnAddress_AreAnError() {
        testWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.sweepUnlocked(MoneroTxConfig()) }
            assertFailsWith<MoneroError> { wallet.sweepOutput(MoneroTxConfig()) }
        }
    }

    @Test
    fun sweeps_OfAClosedWallet_Fail() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        assertFailsWith<MoneroError> { wallet.sweepOutput(MoneroTxConfig()) }
        assertFailsWith<MoneroError> { wallet.sweepUnlocked(MoneroTxConfig()) }
        assertFailsWith<MoneroError> { wallet.sweepDust() }
    }

    // ------------------------------- MULTISIG ----------------------------------

    @Test
    fun aWalletThatIsNotMultisig_SaysSo() {
        testWallet().use { wallet ->
            assertFalse(wallet.isMultisig())
            assertFalse(wallet.isMultisigImportNeeded())
            val info = wallet.getMultisigInfo()
            assertEquals(false, info.isMultisig)
            assertEquals(false, info.isReady)
            assertNull(info.threshold)
            assertNull(info.numParticipants)
        }
    }

    @Test
    fun prepareMultisig_ReturnsTheHexOfTheWallet() {
        MoneroWalletFull.createWallet(config()).use { first ->
            MoneroWalletFull.createWallet(config()).use { second ->
                val hex = first.prepareMultisig()
                assertTrue(hex.isNotEmpty())
                assertTrue(hex != second.prepareMultisig(), "each wallet prepares a hex of its own")
            }
        }
    }

    @Test
    fun makeMultisig_WithHexesThatAreNotPrepared_IsAnError() {
        MoneroWalletFull.createWallet(config()).use { wallet ->
            wallet.prepareMultisig()
            val error = assertFailsWith<MoneroError> { wallet.makeMultisig(listOf("asd", "dsa"), 2, "") }
            assertEquals("Kex message unexpectedly small.", error.message)
        }
    }

    @Test
    fun aWalletInTheMiddleOfTheSetup_IsMultisigButNotReady() {
        MoneroWalletFull.createWallet(config()).use { first ->
            MoneroWalletFull.createWallet(config()).use { second ->
                val prepared = second.prepareMultisig()
                first.prepareMultisig()
                first.makeMultisig(listOf(prepared), 2, "")

                assertTrue(first.isMultisig())
                assertEquals(false, first.getMultisigInfo().isReady)

                // a multisig wallet that is not final has no seed to read
                val error = assertFailsWith<MoneroError> { first.getSeed() }
                assertEquals("This wallet is multisig, but not yet finalized", error.message)
            }
        }
    }

    @Test
    fun exchangeMultisigKeys_WithHexesThatAreNotOfTheRound_IsAnError() {
        MoneroWalletFull.createWallet(config()).use { wallet ->
            wallet.prepareMultisig()
            assertTrue(assertFailsWith<MoneroError> { wallet.exchangeMultisigKeys(listOf("asd", "dsa"), "") }.message!!.isNotEmpty())
        }
    }

    // the setups of the multisig sample code of monero-python
    @Test
    fun theSetup_Of1Of2_MakesAFinalMultisigWallet() {
        assertFinalMultisig(1, 2)
    }

    @Test
    fun theSetup_Of1Of4_MakesAFinalMultisigWallet() {
        assertFinalMultisig(1, 4)
    }

    @Test
    fun theSetup_Of2Of2_MakesAFinalMultisigWallet() {
        assertFinalMultisig(2, 2)
    }

    @Test
    fun theSetup_Of2Of3_MakesAFinalMultisigWallet() {
        assertFinalMultisig(2, 3)
    }

    @Test
    fun theSetup_Of2Of4_MakesAFinalMultisigWallet() {
        assertFinalMultisig(2, 4)
    }

    @Test
    fun aFinalMultisigWallet_ExportsItsHex() {
        MultisigSupport.create(2, 2, { config() }).use { setup ->
            val hex = setup.participants[0].exportMultisigHex()
            assertTrue(hex.isNotEmpty())
            assertTrue(hex != setup.participants[1].exportMultisigHex(), "each participant exports a hex of its own")
        }
    }

    @Test
    fun exportMultisigHex_OfAWalletThatIsNotMultisig_IsAnError() {
        testWallet().use { wallet ->
            assertFailsWith<MoneroError> { wallet.exportMultisigHex() }
        }
    }

    @Test
    fun importMultisigHex_OfHexesThatAreNotOnes_IsAnError() {
        MultisigSupport.create(2, 2, { config() }).use { setup ->
            assertFailsWith<MoneroError> { setup.participants[0].importMultisigHex(listOf("asd", "dsa"), false) }
        }
    }

    @Test
    fun signMultisigTxHex_OfAHexThatIsNotATx_IsAnError() {
        MultisigSupport.create(2, 2, { config() }).use { setup ->
            assertFailsWith<MoneroError> { setup.participants[0].signMultisigTxHex("this is not a multisig transaction") }
        }
    }

    @Test
    fun submitMultisigTxHex_OfAHexThatIsNotATx_IsAnError() {
        MultisigSupport.create(2, 2, { config() }).use { setup ->
            assertFailsWith<MoneroError> { setup.participants[0].submitMultisigTxHex("this is not a signed multisig transaction") }
        }
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
            { wallet.getDaemonConnection() },
            { wallet.setDaemonConnection(MoneroRpcConnection(offlineServerUri)) },
            { wallet.isDaemonTrusted() },
            { wallet.getHeight() },
            { wallet.getBalance() },
            { wallet.getUnlockedBalance() },
            { wallet.isSynced() },
            { wallet.sync() },
            { wallet.startSyncing(5000) },
            { wallet.stopSyncing() },
            { wallet.waitForNextBlock(100) },
            { wallet.getTxs() },
            { wallet.createTxs(MoneroTxConfig()) },
            { wallet.relayTx("metadata") },
            { wallet.relayTx(MoneroTxWallet()) },
            { wallet.isMultisig() },
            { wallet.isMultisigImportNeeded() },
            { wallet.getMultisigInfo() },
            { wallet.prepareMultisig() },
            { wallet.makeMultisig(listOf("hex"), 2, "") },
            { wallet.exchangeMultisigKeys(listOf("hex"), "") },
            { wallet.exportMultisigHex() },
            { wallet.importMultisigHex(listOf("hex"), false) },
            { wallet.signMultisigTxHex("hex") },
            { wallet.submitMultisigTxHex("hex") },
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

    // the test wallet that the python suite shares as a class fixture, restored from its seed and left in memory
    private fun testWallet(): MoneroWalletFull {
        val config = config()
        config.seed = seed
        return MoneroWalletFull.createWallet(config)
    }

    // the participants make a wallet that is final: it has the address of the setup, answers its threshold and its
    // participants, and has a seed, which is the multisig data of the wallet
    private fun assertFinalMultisig(m: Int, n: Int) {
        MultisigSupport.create(m, n, { config() }).use { setup ->
            validateAddress(setup.address)
            for (wallet in setup.participants) {
                assertEquals(setup.address, wallet.getPrimaryAddress())
                val info = wallet.getMultisigInfo()
                assertEquals(true, info.isMultisig)
                assertEquals(true, info.isReady)
                assertEquals(m, info.threshold)
                assertEquals(n, info.numParticipants)
                assertTrue(wallet.isMultisig())
                assertTrue(wallet.getSeed().isNotEmpty())
                assertFalse(wallet.isViewOnly())
            }
        }
    }

    private fun deleteTheCache(path: String) {
        assertTrue(Files.deleteIfExists(Path.of(path)), "the wallet has written its cache at $path")
    }

    private fun walletPath(): String {
        return tempDir.resolve("wallet-${System.nanoTime()}").toString()
    }

    // ------------------------------- THE INTERFACE ----------------------------------

    @Test
    fun wallet_IsAMoneroWallet() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val asInterface: MoneroWallet = wallet
            assertEquals(wallet.getPrimaryAddress(), asInterface.getPrimaryAddress())
            assertEquals(wallet.getPath(), asInterface.getPath())
            assertTrue(asInterface is MoneroWalletDefault)
        }
    }

    // the sync of a wallet that has no node fails the same with a listener, which is not notified
    @Test
    fun sync_WithAListener_OfAWalletThatIsNotConnected_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val listener = object : MoneroWalletListener() {
                var notified = 0
                override fun onSyncProgress(height: Long, startHeight: Long, endHeight: Long, percentDone: Double, message: String?) {
                    notified++
                }
            }
            assertEquals("Wallet is not connected to daemon", assertFailsWith<MoneroError> { wallet.sync(null, listener) }.message)
            assertEquals("Wallet is not connected to daemon", assertFailsWith<MoneroError> { wallet.sync(listener) }.message)
            assertEquals(0, listener.notified)
            assertEquals(emptyList(), wallet.getListeners())
        }
    }

    @Test
    fun balances_OfAnAccountOrASubaddress_AreTheOnesOfThem() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals(BigInteger.ZERO, wallet.getBalance())
            assertEquals(BigInteger.ZERO, wallet.getBalance(null, null))
            assertEquals(BigInteger.ZERO, wallet.getUnlockedBalance())
            assertEquals(BigInteger.ZERO, wallet.getBalance(0))
            assertEquals(BigInteger.ZERO, wallet.getBalance(0, 0))
            assertEquals(BigInteger.ZERO, wallet.getUnlockedBalance(0))
            assertEquals(BigInteger.ZERO, wallet.getUnlockedBalance(0, 1))
        }
    }

    @Test
    fun balances_OfASubaddressWithoutAnAccount_Fail() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getBalance(null, 0) }
            assertEquals("Cannot provide a subaddress index without an account index", error.message)
            assertFailsWith<MoneroError> { wallet.getUnlockedBalance(null, 0) }
        }
    }

    // monero-cpp derives the addresses of an account that the wallet has not made, as it does for the addresses out of range, so the
    // account has nothing: its balance is zero, and it is not an error
    @Test
    fun balances_OfAnAccountThatWasNotMade_AreZero() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals(1, wallet.getAccounts().size)
            assertEquals(BigInteger.ZERO, wallet.getBalance(99))
            assertEquals(BigInteger.ZERO, wallet.getUnlockedBalance(99, 0))
        }
    }

    // what the wallet does not do yet is the default of the interface, which says that it is not supported
    @Test
    fun functionsNotWrittenYet_AreNotSupported() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val calls = mapOf<String, () -> Unit>(
                "getAccountTags" to { wallet.getAccountTags() },
                "tagAccounts" to { wallet.tagAccounts("savings", listOf(0)) }
            )
            for ((name, call) in calls) {
                val error = assertFailsWith<MoneroError>(name) { call() }
                assertTrue("not supported" in error.message!!, "$name: ${error.message}")
            }
        }
    }

    @Test
    fun waitForNextBlock_OfAClosedWallet_Fails() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        assertFailsWith<MoneroError> { wallet.waitForNextBlock() }
    }

    // ------------------------------- ACCOUNTS ----------------------------------

    @Test
    fun getAccounts_WithATag_IsNotSupported() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getAccounts(false, "savings") }
            assertTrue("no tags of accounts" in error.message!!, error.message)
        }
    }

    @Test
    fun accounts_OfAClosedWallet_Fail() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        assertFailsWith<MoneroError> { wallet.getAccounts() }
        assertFailsWith<MoneroError> { wallet.createAccount() }
        assertFailsWith<MoneroError> { wallet.createSubaddress(0) }
        assertFailsWith<MoneroError> { wallet.setSubaddressLabel(0, 0, "label") }
        assertFailsWith<MoneroError> { wallet.getAddressIndex(wallet.javaClass.name) }
    }

    @Test
    fun createAccount_OfAWalletOnDisk_IsKeptAfterASave() {
        val path = walletPath()
        val label = "kept"
        MoneroWalletFull.createWalletRandom(config(path)).use { wallet ->
            wallet.createAccount(label)
            wallet.save()
        }
        MoneroWalletFull.openWallet(config(path)).use { wallet ->
            assertEquals(2, wallet.getAccounts().size)
            assertEquals(label, wallet.getSubaddress(1, 0).label)
        }
    }

    // ------------------------------- TRANSFERS ----------------------------------

    // like the transactions, the transfers of a wallet come from a node
    @Test
    fun transfers_OfAWalletThatIsNotConnected_AreAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals("no connection to daemon", assertFailsWith<MoneroError> { wallet.getTransfers() }.message)
            assertEquals("no connection to daemon", assertFailsWith<MoneroError> { wallet.getTransfers(0) }.message)
            assertEquals("no connection to daemon", assertFailsWith<MoneroError> { wallet.getTransfers(MoneroTransferQuery().also { it.isIncoming = true }) }.message)
        }
    }

    @Test
    fun transfers_WithAnIndexThatIsNegative_AreAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertFailsWith<MoneroError> { wallet.getTransfers(MoneroTransferQuery().also { it.accountIndex = -1 }) }
            assertFailsWith<MoneroError> { wallet.getTransfers(MoneroTransferQuery().also { it.accountIndex = 0; it.subaddressIndex = -1 }) }
            assertFailsWith<MoneroError> { wallet.getTransfers(MoneroTransferQuery().also { it.subaddressIndices = listOf(1, -2) }) }
            assertFailsWith<MoneroError> { wallet.getTransfers(-1, 0) }
        }
    }

    @Test
    fun transfers_OfAClosedWallet_Fail() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        assertFailsWith<MoneroError> { wallet.getTransfers() }
    }

    // ------------------------------- OUTPUTS AND KEY IMAGES ----------------------------------

    @Test
    fun outputs_OfAWalletThatHasNone_AreEmpty() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertTrue(wallet.getOutputs().isEmpty())
            assertTrue(wallet.getOutputs(MoneroOutputQuery().also { it.accountIndex = 0 }).isEmpty())
        }
    }

    @Test
    fun outputs_WithAKeyImageThatIsNotTheWallets_CanNotBeFrozenThawedOrChecked() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val unknown = "a".repeat(64)
            assertFailsWith<MoneroError> { wallet.freezeOutput(unknown) }
            assertFailsWith<MoneroError> { wallet.thawOutput(unknown) }
            assertFailsWith<MoneroError> { wallet.isOutputFrozen(unknown) }
            assertFailsWith<MoneroError> { wallet.freezeOutput("") }
        }
    }

    @Test
    fun outputsAndKeyImages_OfAClosedWallet_Fail() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        assertFailsWith<MoneroError> { wallet.getOutputs() }
        assertFailsWith<MoneroError> { wallet.exportOutputs() }
        assertFailsWith<MoneroError> { wallet.importOutputs("") }
        assertFailsWith<MoneroError> { wallet.exportKeyImages() }
        assertFailsWith<MoneroError> { wallet.importKeyImages(emptyList()) }
        assertFailsWith<MoneroError> { wallet.freezeOutput("a".repeat(64)) }
        assertFailsWith<MoneroError> { wallet.thawOutput("a".repeat(64)) }
        assertFailsWith<MoneroError> { wallet.isOutputFrozen("a".repeat(64)) }
    }

    @Test
    fun exportKeyImages_OfAWalletThatHasNoOutputs_HasNone() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertTrue(wallet.exportKeyImages(true).keyImages.isNullOrEmpty())
            assertEquals(0, wallet.exportKeyImages().keyImages?.size ?: 0)
        }
    }

    // ------------------------------- NOTES OF TRANSACTIONS ----------------------------------

    private val hash1 = "a".repeat(64)
    private val hash2 = "b".repeat(64)

    @Test
    fun txNote_OfATxThatHasNone_IsEmpty() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals("", wallet.getTxNote(hash1))
            assertEquals(listOf("", ""), wallet.getTxNotes(listOf(hash1, hash2)))
        }
    }

    @Test
    fun txNote_IsWhatWasSet() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            wallet.setTxNote(hash1, "pizza")
            assertEquals("pizza", wallet.getTxNote(hash1))
            assertEquals("", wallet.getTxNote(hash2))

            wallet.setTxNote(hash1, "pizza and a beer, with unicode: é 日本")
            assertEquals("pizza and a beer, with unicode: é 日本", wallet.getTxNote(hash1))

            // an empty note takes the note away
            wallet.setTxNote(hash1, "")
            assertEquals("", wallet.getTxNote(hash1))
        }
    }

    @Test
    fun txNotes_AreWhatWasSet_InTheOrderOfTheHashes() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            wallet.setTxNotes(listOf(hash1, hash2), listOf("first", "second"))
            assertEquals(listOf("first", "second"), wallet.getTxNotes(listOf(hash1, hash2)))
            assertEquals(listOf("second", "first", "second"), wallet.getTxNotes(listOf(hash2, hash1, hash2)))
            assertEquals("first", wallet.getTxNote(hash1))
            assertEquals(emptyList(), wallet.getTxNotes(emptyList()))
            wallet.setTxNotes(emptyList(), emptyList())
        }
    }

    @Test
    fun txNotes_WithAsManyHashesAsNotes_AreRequired() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertFailsWith<MoneroError> { wallet.setTxNotes(listOf(hash1, hash2), listOf("only one")) }
            assertFailsWith<MoneroError> { wallet.setTxNotes(listOf(hash1), listOf("one", "two")) }
            assertEquals("", wallet.getTxNote(hash1), "no note is set by a call that failed")
        }
    }

    @Test
    fun txNotes_OfWhatIsNotAHash_AreAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            for (invalid in listOf("invalid_id", "", "a".repeat(63), "z".repeat(64))) {
                assertFailsWith<MoneroError>(invalid) { wallet.getTxNote(invalid) }
                assertFailsWith<MoneroError>(invalid) { wallet.setTxNote(invalid, "note") }
                assertFailsWith<MoneroError>(invalid) { wallet.getTxNotes(listOf(hash1, invalid)) }
                assertFailsWith<MoneroError>(invalid) { wallet.setTxNotes(listOf(hash1, invalid), listOf("one", "two")) }
            }
            assertEquals("", wallet.getTxNote(hash1), "no note is set by a call that failed")
        }
    }

    @Test
    fun txNotes_AreKeptInTheFileOfTheWallet() {
        val path = walletPath()
        MoneroWalletFull.createWalletRandom(config(path)).use { wallet ->
            wallet.setTxNote(hash1, "kept")
            wallet.setTxNotes(listOf(hash2), listOf("kept too"))
            wallet.save()
        }
        MoneroWalletFull.openWallet(config(path)).use { wallet ->
            assertEquals(listOf("kept", "kept too"), wallet.getTxNotes(listOf(hash1, hash2)))
        }
    }

    @Test
    fun txNotes_OfAClosedWallet_Fail() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        assertFailsWith<MoneroError> { wallet.getTxNote(hash1) }
        assertFailsWith<MoneroError> { wallet.getTxNotes(listOf(hash1)) }
        assertFailsWith<MoneroError> { wallet.setTxNote(hash1, "note") }
        assertFailsWith<MoneroError> { wallet.setTxNotes(listOf(hash1), listOf("note")) }
    }

    // ------------------------------- KEYS AND PROOFS OF TRANSACTIONS ----------------------------------

    @Test
    fun txKeysAndProofs_OfATxThatTheWalletDoesNotHave_AreAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val address = wallet.getPrimaryAddress()
            assertFailsWith<MoneroError> { wallet.getTxKey(hash1) }
            assertFailsWith<MoneroError> { wallet.getTxProof(hash1, address) }
            assertFailsWith<MoneroError> { wallet.getSpendProof(hash1) }
        }
    }

    @Test
    fun txKeysAndProofs_OfWhatIsNotAHash_AreAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val address = wallet.getPrimaryAddress()
            val invalid = "TX hash has invalid format"
            assertEquals(invalid, assertFailsWith<MoneroError> { wallet.getTxKey("invalid_id") }.message)
            assertEquals(invalid, assertFailsWith<MoneroError> { wallet.checkTxKey("invalid_id", hash1, address) }.message)
            assertEquals(invalid, assertFailsWith<MoneroError> { wallet.getTxProof("invalid_id", address) }.message)
            assertEquals(invalid, assertFailsWith<MoneroError> { wallet.checkTxProof("invalid_id", address, null, "signature") }.message)
            assertEquals(invalid, assertFailsWith<MoneroError> { wallet.getSpendProof("invalid_id") }.message)
            assertEquals(invalid, assertFailsWith<MoneroError> { wallet.checkSpendProof("invalid_id", null, "signature") }.message)
        }
    }

    @Test
    fun checkTxKeyAndProof_WithAKeyOrAnAddressThatIsNotValid_AreAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val address = wallet.getPrimaryAddress()
            assertEquals("Tx key has invalid format", assertFailsWith<MoneroError> { wallet.checkTxKey(hash1, "invalid_tx_key", address) }.message)
            assertEquals("Invalid address", assertFailsWith<MoneroError> { wallet.checkTxKey(hash1, hash2, "invalid_address") }.message)
            assertEquals("Invalid address", assertFailsWith<MoneroError> { wallet.getTxProof(hash1, "invalid_address") }.message)
            assertEquals("Invalid address", assertFailsWith<MoneroError> { wallet.checkTxProof(hash1, "invalid_address", null, "signature") }.message)
        }
    }

    @Test
    fun txKeysAndProofs_OfAClosedWallet_Fail() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        val address = wallet.getPrimaryAddress()
        wallet.close()
        assertFailsWith<MoneroError> { wallet.getTxKey(hash1) }
        assertFailsWith<MoneroError> { wallet.checkTxKey(hash1, hash2, address) }
        assertFailsWith<MoneroError> { wallet.getTxProof(hash1, address) }
        assertFailsWith<MoneroError> { wallet.checkTxProof(hash1, address, null, "signature") }
        assertFailsWith<MoneroError> { wallet.getSpendProof(hash1) }
        assertFailsWith<MoneroError> { wallet.checkSpendProof(hash1, null, "signature") }
    }

    // ------------------------------- PROOFS OF RESERVES ----------------------------------

    @Test
    fun reserveProofs_OfAccountsThatCanNotBeProved_AreAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals("Account index is out of bound", assertFailsWith<MoneroError> { wallet.getReserveProofAccount(99, BigInteger.ONE, "message") }.message)
            // a wallet that has nothing is refused before the amount is looked at
            assertEquals("Zero balance", assertFailsWith<MoneroError> { wallet.getReserveProofAccount(0, BigInteger.ONE, "message") }.message)
            assertFailsWith<MoneroError> { wallet.getReserveProofAccount(0, BigInteger.ONE.negate(), "message") }
            assertFailsWith<MoneroError> { wallet.getReserveProofAccount(0, BigInteger.ONE.shiftLeft(64), "message") }
        }
    }

    @Test
    fun checkReserveProof_WithAnAddressThatIsNotValid_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals("Invalid address", assertFailsWith<MoneroError> { wallet.checkReserveProof("invalid_address", null, "signature") }.message)
        }
    }

    // the check of a signature needs a daemon, which the tests of the proofs against a regtest node have
    @Test
    fun checkReserveProof_OfAWalletThatIsNotConnected_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertFailsWith<MoneroError> { wallet.checkReserveProof(wallet.getPrimaryAddress(), null, "signature") }
        }
    }

    @Test
    fun reserveProofs_OfAClosedWallet_Fail() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        val address = wallet.getPrimaryAddress()
        wallet.close()
        assertFailsWith<MoneroError> { wallet.getReserveProofWallet("message") }
        assertFailsWith<MoneroError> { wallet.getReserveProofAccount(0, BigInteger.ONE, "message") }
        assertFailsWith<MoneroError> { wallet.checkReserveProof(address, "message", "signature") }
    }

    // ------------------------------- ADDRESS BOOK ----------------------------------

    // an address that is not the one of the wallet, which monero-python gets from its test utils
    private fun externalAddress(): String = MoneroWalletFull.createWalletRandom(config(walletPath())).use { it.getPrimaryAddress() }

    private fun assertEntry(entry: MoneroAddressBookEntry, index: Long, address: String, description: String) {
        assertEquals(index, entry.index)
        assertEquals(address, entry.address)
        assertEquals(description, entry.description)
        assertNull(entry.paymentId)
    }

    @Test
    fun addressBook_OfANewWallet_IsEmpty() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals(emptyList(), wallet.getAddressBookEntries())
            assertEquals(emptyList(), wallet.getAddressBookEntries(emptyList()))
        }
    }

    // Has an address book: adds standard addresses, gets them all or by index, edits them and deletes them
    @Test
    fun addressBook_AddsEditsAndDeletesEntries() {
        val address = externalAddress()
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val indices = (0 until 5).map { wallet.addAddressBookEntry(address, "hi there!") }
            assertEquals(listOf(0L, 1L, 2L, 3L, 4L), indices)

            val entries = wallet.getAddressBookEntries()
            assertEquals(5, entries.size)
            entries.forEachIndexed { i, entry -> assertEntry(entry, indices[i], address, "hi there!") }

            for (index in indices) wallet.editAddressBookEntry(index, null, "hello there!!")
            wallet.getAddressBookEntries(indices).forEachIndexed { i, entry -> assertEntry(entry, indices[i], address, "hello there!!") }

            // each delete takes the entry at the index, and the entries after it move one place down
            repeat(5) { wallet.deleteAddressBookEntry(indices[0]) }
            assertEquals(emptyList(), wallet.getAddressBookEntries())
        }
    }

    @Test
    fun addressBook_GetsTheEntriesOfTheIndices_InTheOrderOfTheIndices() {
        val address = externalAddress()
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            for (i in 0 until 4) wallet.addAddressBookEntry(address, "entry $i")
            assertEquals(listOf("entry 3", "entry 1"), wallet.getAddressBookEntries(listOf(3L, 1L)).map { it.description })
            assertEquals(listOf("entry 2"), wallet.getAddressBookEntries(listOf(2L)).map { it.description })
        }
    }

    @Test
    fun addressBook_DeleteMovesTheEntriesAfterItOnePlaceDown() {
        val address = externalAddress()
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            for (i in 0 until 4) wallet.addAddressBookEntry(address, "entry $i")
            wallet.deleteAddressBookEntry(1)
            val entries = wallet.getAddressBookEntries()
            assertEquals(listOf(0L, 1L, 2L), entries.map { it.index })
            assertEquals(listOf("entry 0", "entry 2", "entry 3"), entries.map { it.description })
        }
    }

    // Adds subaddresses and integrated addresses, which come back as they were given and with no payment id
    @Test
    fun addressBook_KeepsSubaddressesAndIntegratedAddresses() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val subaddress = wallet.getAddress(0, 1)
            val integrated = (0 until 5).map { wallet.getIntegratedAddress(null, "03284e41c342f03$it").integratedAddress!! }
            val subaddressIndex = wallet.addAddressBookEntry(subaddress, "a subaddress")
            val integratedIndices = integrated.mapIndexed { i, address -> wallet.addAddressBookEntry(address, "integrated $i") }

            assertEntry(wallet.getAddressBookEntries(listOf(subaddressIndex)).single(), subaddressIndex, subaddress, "a subaddress")
            wallet.getAddressBookEntries(integratedIndices).forEachIndexed { i, entry -> assertEntry(entry, integratedIndices[i], integrated[i], "integrated $i") }
            assertEquals(6, wallet.getAddressBookEntries().size)
        }
    }

    @Test
    fun addressBook_EditKeepsWhatIsNull() {
        val address = externalAddress()
        val other = externalAddress()
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val index = wallet.addAddressBookEntry(address, "first")

            wallet.editAddressBookEntry(index, other, null)
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, other, "first")

            wallet.editAddressBookEntry(index, null, "second")
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, other, "second")

            wallet.editAddressBookEntry(index, address, "third")
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, address, "third")

            // an empty description takes the description away, and nothing to change changes nothing
            wallet.editAddressBookEntry(index, null, "")
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, address, "")
            wallet.editAddressBookEntry(index, null, null)
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, address, "")
        }
    }

    @Test
    fun addressBook_EntryWithNoDescription_HasAnEmptyOne() {
        val address = externalAddress()
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val index = wallet.addAddressBookEntry(address)
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, address, "")
        }
    }

    @Test
    fun addressBook_WithWhatIsNotValid_IsAnError() {
        val address = externalAddress()
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertFailsWith<MoneroError> { wallet.addAddressBookEntry("invalid_address", "no") }
            assertEquals(emptyList(), wallet.getAddressBookEntries())

            val index = wallet.addAddressBookEntry(address, "yes")
            assertFailsWith<MoneroError> { wallet.editAddressBookEntry(index, "invalid_address", "no") }
            assertFailsWith<MoneroError> { wallet.editAddressBookEntry(index + 1, null, "no") }
            assertFailsWith<MoneroError> { wallet.deleteAddressBookEntry(index + 1) }
            assertFailsWith<MoneroError> { wallet.getAddressBookEntries(listOf(index + 1)) }
            assertFailsWith<MoneroError> { wallet.getAddressBookEntries(listOf(-1L)) }
            assertEntry(wallet.getAddressBookEntries().single(), index, address, "yes")
        }
    }

    @Test
    fun addressBook_IsKeptInTheFileOfTheWallet() {
        val address = externalAddress()
        val path = walletPath()
        MoneroWalletFull.createWalletRandom(config(path)).use { wallet ->
            wallet.addAddressBookEntry(address, "kept")
            wallet.addAddressBookEntry(wallet.getAddress(0, 1), "also kept")
            wallet.save()
        }
        MoneroWalletFull.openWallet(config(path)).use { wallet ->
            assertEquals(listOf("kept", "also kept"), wallet.getAddressBookEntries().map { it.description })
            assertEquals(address, wallet.getAddressBookEntries().first().address)
        }
    }

    @Test
    fun addressBook_OfAClosedWallet_Fails() {
        val address = externalAddress()
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        assertFailsWith<MoneroError> { wallet.getAddressBookEntries() }
        assertFailsWith<MoneroError> { wallet.addAddressBookEntry(address, "no") }
        assertFailsWith<MoneroError> { wallet.editAddressBookEntry(0, null, "no") }
        assertFailsWith<MoneroError> { wallet.deleteAddressBookEntry(0) }
    }

    // ------------------------------- LISTENERS ----------------------------------

    @Test
    fun listeners_OfANewWallet_AreNone() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals(emptyList(), wallet.getListeners())
        }
    }

    // Can stop listening: the listeners are the ones that were added, in order, and none after they are removed
    @Test
    fun listeners_AreWhatWasAdded_UntilTheyAreRemoved() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val first = MoneroWalletListener()
            val second = MoneroWalletListener()
            wallet.addListener(first)
            assertEquals(listOf(first), wallet.getListeners())
            wallet.addListener(second)
            assertEquals(listOf(first, second), wallet.getListeners())

            // adding one again leaves it as it is
            wallet.addListener(first)
            assertEquals(listOf(first, second), wallet.getListeners())

            wallet.removeListener(first)
            assertEquals(listOf(second), wallet.getListeners())
            wallet.removeListener(second)
            assertEquals(emptyList(), wallet.getListeners())

            // the one that was removed can be added again
            wallet.addListener(first)
            assertEquals(listOf(first), wallet.getListeners())
        }
    }

    @Test
    fun removeListeners_RemovesAllOfThem() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            repeat(3) { wallet.addListener(MoneroWalletListener()) }
            assertEquals(3, wallet.getListeners().size)
            wallet.removeListeners()
            assertEquals(emptyList(), wallet.getListeners())
            wallet.removeListeners()
            wallet.addListener(MoneroWalletListener())
            assertEquals(1, wallet.getListeners().size)
        }
    }

    @Test
    fun removeListener_ThatIsNotRegistered_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.removeListener(MoneroWalletListener()) }
            assertEquals("Listener is not registered with wallet", error.message)

            val listener = MoneroWalletListener()
            wallet.addListener(listener)
            wallet.removeListener(listener)
            assertFailsWith<MoneroError> { wallet.removeListener(listener) }
        }
    }

    // a wallet that is closed with listeners registered frees them, and does not wait for them
    @Test
    fun close_OfAWalletWithListeners_Returns() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        repeat(3) { wallet.addListener(MoneroWalletListener()) }
        wallet.close()
        assertTrue(wallet.isClosed())
    }

    @Test
    fun listeners_OfAClosedWallet_Fail() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        val listener = MoneroWalletListener()
        wallet.addListener(listener)
        wallet.close()
        assertFailsWith<MoneroError> { wallet.addListener(MoneroWalletListener()) }
        assertFailsWith<MoneroError> { wallet.removeListener(listener) }
        assertFailsWith<MoneroError> { wallet.getListeners() }
        assertFailsWith<MoneroError> { wallet.removeListeners() }
        assertFailsWith<MoneroError> { wallet.sync(listener) }
    }

    // ------------------------------- SCAN AND RESCAN ----------------------------------

    @Test
    fun scanTxs_WithWhatIsNotAValidHash_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals("Invalid transaction hash: zz", assertFailsWith<MoneroError> { wallet.scanTxs(listOf(hash1, "zz")) }.message)
            assertFailsWith<MoneroError> { wallet.scanTxs(listOf("a".repeat(63))) }
        }
    }

    @Test
    fun scanTxs_WithNoHash_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals("No tx hashes given to scan", assertFailsWith<MoneroError> { wallet.scanTxs(emptyList()) }.message)
        }
    }

    // the transactions come from the daemon, which a wallet with no node has not
    @Test
    fun scanTxs_OfAWalletThatIsNotConnected_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals("Failed to get transaction from daemon", assertFailsWith<MoneroError> { wallet.scanTxs(listOf(hash1)) }.message)
        }
    }

    @Test
    fun rescans_OfAWalletThatIsNotConnected_AreAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals("Rescan spent can only be used with a trusted daemon", assertFailsWith<MoneroError> { wallet.rescanSpent() }.message)
            assertEquals("Wallet is not connected to daemon", assertFailsWith<MoneroError> { wallet.rescanBlockchain() }.message)
        }
    }

    @Test
    fun scansAndRescans_OfAClosedWallet_Fail() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        assertFailsWith<MoneroError> { wallet.scanTxs(listOf(hash1)) }
        assertFailsWith<MoneroError> { wallet.rescanSpent() }
        assertFailsWith<MoneroError> { wallet.rescanBlockchain() }
    }

    // ------------------------------- MINING ----------------------------------

    // the daemon of a wallet that has none is not trusted, and mines for no one
    @Test
    fun mining_OfAWalletThatIsNotConnected_IsAnError() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            assertEquals("This command requires a trusted daemon.", assertFailsWith<MoneroError> { wallet.startMining(1, false, true) }.message)
            assertEquals("Couldn't stop mining due to unknown error.", assertFailsWith<MoneroError> { wallet.stopMining() }.message)
        }
    }

    @Test
    fun mining_OfAClosedWallet_Fails() {
        val wallet = MoneroWalletFull.createWalletRandom(config(walletPath()))
        wallet.close()
        assertFailsWith<MoneroError> { wallet.startMining(1, false, true) }
        assertFailsWith<MoneroError> { wallet.stopMining() }
    }
}
