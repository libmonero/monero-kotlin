package monero.wallet

import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.ModelTestSupport.assertSameJson
import monero.NativeTestSupport
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.common.MoneroRpcConnection
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroMessageSignatureType.SIGN_WITH_SPEND_KEY
import monero.wallet.model.MoneroMessageSignatureType.SIGN_WITH_VIEW_KEY
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroTxWallet
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Tag

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
@Tag("unit")
class MoneroWalletFullTest {

    // the test wallet of monero-python's config.ini, which its suites restore from the seed
    private val seed = "vortex degrees outbreak teeming gimmick school rounded tonic observant injury leech ought problems ahead upcoming ledge textbook cigar atrium trash dunes eavesdrop dullness evolved vortex"
    private val primaryAddress = "48W9YHwPzRz9aPTeXCA6kmSpW6HsvmWx578jj3of2gT3JwZzwTf33amESBoNDkL6SVK34Q2HTKqgYbGyE1hBws3wCrcBDR2"
    private val privateViewKey = "e8c2288181bad9ec410d7322efd65f663c6da57bd1d1198636278a039743a600"
    private val privateSpendKey = "be7a2f71097f146bdf0fb5bb8edfe2240a9767e15adee74d95af1b5a64f29a0c"
    private val publicViewKey = "42e465bdcd00de50516f1c7049bbe26bd3c11195e8dae5cceb38bad92d484269"
    private val publicSpendKey = "b58d33a1dac23d334539cbed3657b69a5c967d6860357e24ab4d11899a312a6b"

    // the payment id of the python tests of integrated addresses
    private val paymentId = "03284e41c342f036"

    // the seed of monero-python's test_monero_wallet_common.ini, which is two words short of a mnemonic
    private val brokenSeed = "memoir desk algebra inbound innocent unplugs fully okay five inflamed giant factual ritual toyed topic snake unhappy guarded tweezers haunted inundate giant"

    // the server that python gives a wallet which must not reach a daemon
    private val offlineServerUri = "offline_server_uri"

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

    // ------------------------------- SEED AND KEYS ----------------------------------

    @Test
    fun getSeed_ReadsTheTestWallet() {
        testWallet().use { wallet ->
            val seed = wallet.getSeed()
            validateMnemonic(seed)
            assertEquals(this.seed, seed)
        }
    }

    @Test
    fun getSeedLanguage_IsTheDefaultLanguage() {
        testWallet().use { wallet ->
            assertEquals(MoneroWalletFull.DEFAULT_LANGUAGE, wallet.getSeedLanguage())
        }
    }

    @Test
    fun getSeedLanguage_IsTheLanguageOfTheConfig() {
        val config = config()
        config.language = "Spanish"
        MoneroWalletFull.createWallet(config).use { wallet ->
            assertEquals("Spanish", wallet.getSeedLanguage())
        }
    }

    @Test
    fun getSeedLanguages_ListsTheLanguagesOfASeed() {
        testWallet().use { wallet ->
            val languages = wallet.getSeedLanguages()
            assertTrue(languages.isNotEmpty())
            for (language in languages) assertTrue(language.isNotEmpty())
            assertTrue(languages.contains(MoneroWalletFull.DEFAULT_LANGUAGE), languages.toString())
        }
    }

    @Test
    fun getSeedLanguages_NeedsNoWallet() {
        val wallet = testWallet()
        wallet.close()
        assertTrue(wallet.getSeedLanguages().contains(MoneroWalletFull.DEFAULT_LANGUAGE))
    }

    @Test
    fun getPrivateViewKey_ReadsTheTestWallet() {
        testWallet().use { wallet ->
            val key = wallet.getPrivateViewKey()
            validatePrivateKey(key)
            assertEquals(privateViewKey, key)
        }
    }

    @Test
    fun getPrivateSpendKey_ReadsTheTestWallet() {
        testWallet().use { wallet ->
            val key = wallet.getPrivateSpendKey()
            validatePrivateKey(key)
            assertEquals(privateSpendKey, key)
        }
    }

    @Test
    fun getPublicViewKey_ReadsTheTestWallet() {
        testWallet().use { wallet ->
            val key = wallet.getPublicViewKey()
            validatePrivateKey(key)
            assertEquals(publicViewKey, key)
        }
    }

    @Test
    fun getPublicSpendKey_ReadsTheTestWallet() {
        testWallet().use { wallet ->
            val key = wallet.getPublicSpendKey()
            validatePrivateKey(key)
            assertEquals(publicSpendKey, key)
        }
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
    fun getPrimaryAddress_ReadsTheTestWallet() {
        testWallet().use { wallet ->
            validateAddress(wallet.getPrimaryAddress())
            assertEquals(primaryAddress, wallet.getPrimaryAddress())
            assertEquals(primaryAddress, wallet.getAddress(0, 0))
        }
    }

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

    @Test
    fun getIntegratedAddress_WithoutAStandardAddress_UsesThePrimaryAddress() {
        testWallet().use { wallet ->
            val integrated = wallet.getIntegratedAddress("", "a".repeat(16))
            assertEquals(primaryAddress, integrated.standardAddress)
            assertEquals("a".repeat(16), integrated.paymentId)
        }
    }

    @Test
    fun getIntegratedAddress_WithoutAPaymentId_IsAnError() {
        testWallet().use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(wallet.getPrimaryAddress(), "") }
            assertEquals("Payment ID shouldn't be left unspecified", error.message)
        }
    }

    @Test
    fun getIntegratedAddress_WithAnInvalidAddress_IsAnError() {
        testWallet().use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress("notanaddress", "a".repeat(16)) }
            assertEquals("Invalid address", error.message)
        }
    }

    @Test
    fun getIntegratedAddress_WithASubaddress_IsAnError() {
        testWallet().use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(wallet.getAddress(0, 1), "a".repeat(16)) }
            assertEquals("Subaddress shouldn't be used", error.message)
        }
    }

    @Test
    fun getIntegratedAddress_WithAnIntegratedAddress_IsAnError() {
        testWallet().use { wallet ->
            val integrated = wallet.getIntegratedAddress("", "a".repeat(16)).integratedAddress
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(integrated, "a".repeat(16)) }
            assertEquals("Already integrated address", error.message)
        }
    }

    @Test
    fun decodeIntegratedAddress_ReadsItBack() {
        testWallet().use { wallet ->
            val integrated = wallet.getIntegratedAddress("", paymentId)
            val decoded = wallet.decodeIntegratedAddress(integrated.integratedAddress!!)
            assertEquals(integrated.standardAddress, decoded.standardAddress)
            assertEquals(integrated.paymentId, decoded.paymentId)
            assertEquals(integrated.integratedAddress, decoded.integratedAddress)

            val invalid = assertFailsWith<MoneroError> { wallet.decodeIntegratedAddress("bad address") }
            assertEquals("Invalid address", invalid.message)

            val paymentIdError = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(wallet.getPrimaryAddress(), "invalid payment id") }
            assertEquals("Invalid payment ID: invalid payment id", paymentIdError.message)
        }
    }

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

    @Test
    fun verifyMessage_WithoutASignatureHeader_IsNotGood() {
        testWallet().use { wallet ->
            assertEquals(false, wallet.verifyMessage("hello", primaryAddress, "not-a-signature").isGood)
        }
    }

    @Test
    fun verifyMessage_ThatIsNotBase58_IsNotGood() {
        testWallet().use { wallet ->
            assertEquals(false, wallet.verifyMessage("hello", primaryAddress, "SigV2!!!not-base58!!!").isGood)
        }
    }

    @Test
    fun verifyMessage_WithATruncatedSignature_IsNotGood() {
        testWallet().use { wallet ->
            val signature = wallet.signMessage("hello", SIGN_WITH_SPEND_KEY, 0, 0)
            val truncated = signature.substring(0, 5) + signature.substring(5, signature.length - 10)
            assertEquals(false, wallet.verifyMessage("hello", primaryAddress, truncated).isGood)
        }
    }

    // ------------------------------- PAYMENT URIS ----------------------------------

    @Test
    fun getPaymentUri_RoundTripsAConfig() {
        testWallet().use { wallet ->
            val config = MoneroTxConfig()
            config.destinations = listOf(MoneroDestination(wallet.getAddress(0, 0), BigInteger.ZERO))
            val uri = wallet.getPaymentUri(config)
            assertSameJson(MoneroJson.serialize(config), MoneroJson.serialize(wallet.parsePaymentUri(uri)))

            config.destinations = listOf(MoneroDestination(wallet.getAddress(0, 1), BigInteger.valueOf(425000000000L)))
            config.recipientName = "John Doe"
            config.note = "OMZG XMR FTW"
            val fullUri = wallet.getPaymentUri(config)
            assertSameJson(MoneroJson.serialize(config), MoneroJson.serialize(wallet.parsePaymentUri(fullUri)))
        }
    }

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

    @Test
    fun theSetup_Of2Of2_MakesAFinalMultisigWallet() {
        assertFinalMultisig(2, 2)
    }

    @Test
    fun theSetup_Of2Of3_MakesAFinalMultisigWallet() {
        assertFinalMultisig(2, 3)
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
    fun getVersion_ReadsTheLibrary() {
        MoneroWalletFull.createWalletRandom(config(walletPath())).use { wallet ->
            val version = wallet.getVersion()
            assertTrue(version.number!! > 0)
            assertNotNull(version.isRelease)
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

    // the test wallet that the python suite shares as a class fixture, restored from its seed and left in memory
    private fun testWallet(): MoneroWalletFull {
        val config = config()
        config.seed = seed
        return MoneroWalletFull.createWallet(config)
    }

    // python validates these with MoneroUtils, which is a test of its own here; the checks read the same shape
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
}
