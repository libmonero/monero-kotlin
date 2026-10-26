package monero.wallet

import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.ModelTestSupport.assertSameJson
import monero.NativeTestSupport
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroAccount
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroMessageSignatureType.SIGN_WITH_SPEND_KEY
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroWalletConfig

/**
 * Mirrors the keys-only wallet tests of monero-python (tests/test_monero_wallet_keys.py over
 * test_monero_wallet_common.py), which run without a daemon: a keys-only wallet has the keys, the seed and the
 * addresses, and nothing else, so the python suite marks the 88 tests that need a chain or a wallet file as not
 * supported and runs the rest against the account of its test seed.
 *
 * Every test calls the real dylib through MoneroWalletKeys, and skips when it is not found. The python suite
 * validates what it reads with MoneroUtils, whose full surface is the utils PR of this repo; these tests check the
 * same shape with their own validators. Two python tests are not mirrored, which the report of this work lists: an
 * invalid signature type cannot be written in Kotlin, whose enumeration has no invalid value, and the errors of the
 * python binding's own not-supported surface, which this class does not expose at all.
 */
class MoneroWalletKeysTest {

    // the test wallet of monero-python's config.ini, which its keys suite restores from the seed
    private val seed = "vortex degrees outbreak teeming gimmick school rounded tonic observant injury leech ought problems ahead upcoming ledge textbook cigar atrium trash dunes eavesdrop dullness evolved vortex"
    private val primaryAddress = "48W9YHwPzRz9aPTeXCA6kmSpW6HsvmWx578jj3of2gT3JwZzwTf33amESBoNDkL6SVK34Q2HTKqgYbGyE1hBws3wCrcBDR2"
    private val privateViewKey = "e8c2288181bad9ec410d7322efd65f663c6da57bd1d1198636278a039743a600"
    private val privateSpendKey = "be7a2f71097f146bdf0fb5bb8edfe2240a9767e15adee74d95af1b5a64f29a0c"
    private val publicViewKey = "42e465bdcd00de50516f1c7049bbe26bd3c11195e8dae5cceb38bad92d484269"
    private val publicSpendKey = "b58d33a1dac23d334539cbed3657b69a5c967d6860357e24ab4d11899a312a6b"

    // the seed of monero-python's test_monero_wallet_common.ini, which is two words short of a mnemonic
    private val brokenSeed = "memoir desk algebra inbound innocent unplugs fully okay five inflamed giant factual ritual toyed topic snake unhappy guarded tweezers haunted inundate giant"

    // the payment id of the python tests of integrated addresses
    private val paymentId = "03284e41c342f036"

    // the accounts and subaddresses that the python keys suite walks
    private val accountIndices = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    private val subaddressIndices = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    @BeforeTest
    fun requireLibrary() {
        NativeTestSupport.assumeAvailable()
    }

    // ------------------------------- CREATE WALLET ----------------------------------

    @Test
    fun createWalletRandom_ReadsItsKeys() {
        val wallet = MoneroWalletKeys.createWalletRandom(config())
        try {
            validateMnemonic(wallet.getSeed())
            validateAddress(wallet.getPrimaryAddress())
            validatePrivateKey(wallet.getPrivateViewKey())
            validatePrivateKey(wallet.getPrivateSpendKey())
            assertEquals(MoneroWalletKeys.DEFAULT_LANGUAGE, wallet.getSeedLanguage())
        } finally {
            wallet.close()
        }
    }

    @Test
    fun createWalletRandom_WithAnUnknownLanguage_IsAnError() {
        val config = config()
        config.language = "english"
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletRandom(config) }
        assertEquals("Unknown language: english", error.message)
    }

    @Test
    fun createWalletFromSeed_ReadsTheTestWallet() {
        val restored = MoneroWalletKeys.createWalletFromSeed(config(seed))
        try {
            assertEquals(primaryAddress, restored.getPrimaryAddress())
            assertEquals(privateViewKey, restored.getPrivateViewKey())
            assertEquals(privateSpendKey, restored.getPrivateSpendKey())
            assertEquals(seed, restored.getSeed())
            assertEquals(MoneroWalletKeys.DEFAULT_LANGUAGE, restored.getSeedLanguage())
        } finally {
            restored.close()
        }
    }

    @Test
    fun createWalletFromSeed_WithABrokenSeed_IsAnError() {
        val config = config(brokenSeed)
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromSeed(config) }
        assertEquals("Invalid mnemonic", error.message)
    }

    @Test
    fun createWalletFromSeedWithAnOffset_ChangesTheWallet() {
        val config = config(seed)
        config.seedOffset = "my secret offset!"
        val wallet = MoneroWalletKeys.createWalletFromSeed(config)
        try {
            validateMnemonic(wallet.getSeed())
            assertTrue(seed != wallet.getSeed())
            validateAddress(wallet.getPrimaryAddress())
            assertTrue(primaryAddress != wallet.getPrimaryAddress())
            assertEquals(MoneroWalletKeys.DEFAULT_LANGUAGE, wallet.getSeedLanguage())
        } finally {
            wallet.close()
        }
    }

    @Test
    fun createWalletFromKeys_FromAllThreeKeys_ReadsTheTestWallet() {
        val config = config()
        config.primaryAddress = primaryAddress
        config.privateViewKey = privateViewKey
        config.privateSpendKey = privateSpendKey
        val wallet = MoneroWalletKeys.createWalletFromKeys(config)
        try {
            assertEquals(primaryAddress, wallet.getPrimaryAddress())
            assertEquals(privateViewKey, wallet.getPrivateViewKey())
            assertEquals(privateSpendKey, wallet.getPrivateSpendKey())
            assertEquals(MoneroNetworkType.MAINNET, wallet.getNetworkType())
            assertFalse(wallet.isClosed())
        } finally {
            wallet.close()
        }
    }

    @Test
    fun createWalletFromKeys_FromTheSpendKey_DerivesTheViewKey() {
        val config = config()
        config.primaryAddress = primaryAddress
        config.privateSpendKey = privateSpendKey
        val wallet = MoneroWalletKeys.createWalletFromKeys(config)
        try {
            assertEquals(primaryAddress, wallet.getPrimaryAddress())
            assertEquals(privateViewKey, wallet.getPrivateViewKey())
            assertEquals(privateSpendKey, wallet.getPrivateSpendKey())
        } finally {
            wallet.close()
        }
    }

    @Test
    fun createWalletFromKeys_FromTheViewKey_IsViewOnly() {
        val config = config()
        config.primaryAddress = primaryAddress
        config.privateViewKey = privateViewKey
        val wallet = MoneroWalletKeys.createWalletFromKeys(config)
        try {
            assertEquals(primaryAddress, wallet.getPrimaryAddress())
            assertEquals(privateViewKey, wallet.getPrivateViewKey())
            assertEquals(MoneroNetworkType.MAINNET, wallet.getNetworkType())
            assertTrue(wallet.isViewOnly())
            val error = assertFailsWith<MoneroError> { wallet.getSeed() }
            assertTrue(error.message!!.contains("watch-only"), error.message)
            assertFalse(wallet.isClosed())
        } finally {
            wallet.close()
        }
    }

    @Test
    fun createWalletFromKeys_WithNoKeys_IsAnError() {
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromKeys(config()) }
        assertEquals("Neither spend key nor view key supplied", error.message)
    }

    @Test
    fun createWalletFromKeys_WithAnInvalidSpendKey_IsAnError() {
        val config = config()
        config.privateSpendKey = "not-a-valid-hex-secret-key"
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromKeys(config) }
        assertEquals("failed to parse secret spend key", error.message)
    }

    @Test
    fun createWalletFromKeys_WithAnInvalidViewKey_IsAnError() {
        val config = config()
        config.primaryAddress = primaryAddress
        config.privateViewKey = "not-a-valid-hex-secret-key"
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromKeys(config) }
        assertEquals("failed to parse secret view key", error.message)
    }

    @Test
    fun createWalletFromKeys_AViewKeyWithoutAnAddress_IsAnError() {
        val config = config()
        config.privateViewKey = "a".repeat(64)
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromKeys(config) }
        assertEquals("must provide address if providing private view key", error.message)
    }

    @Test
    fun createWallet_WithoutANetworkType_IsAnError() {
        val config = MoneroWalletConfig()
        config.seed = seed
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromSeed(config) }
        assertEquals("Must provide wallet network type", error.message)
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
            assertEquals(MoneroWalletKeys.DEFAULT_LANGUAGE, wallet.getSeedLanguage())
        }
    }

    @Test
    fun getSeedLanguages_ListsTheLanguagesOfASeed() {
        testWallet().use { wallet ->
            val languages = wallet.getSeedLanguages()
            assertTrue(languages.isNotEmpty())
            for (language in languages) assertTrue(language.isNotEmpty())
            assertTrue(languages.contains(MoneroWalletKeys.DEFAULT_LANGUAGE), languages.toString())
        }
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
    fun getSubaddressAddress_MatchesTheAccounts() {
        testWallet().use { wallet ->
            assertEquals(wallet.getPrimaryAddress(), wallet.getAddress(0, 0))
            for (account in testAccounts(wallet, true)) {
                validateAddress(account.primaryAddress!!)
                for (subaddress in account.subaddresses!!) {
                    assertEquals(subaddress.address, wallet.getAddress(account.index!!, subaddress.index!!))
                }
            }
        }
    }

    @Test
    fun getSubaddressAddress_OutOfRangeOfTheWalkedIndices_StillAnswers() {
        testWallet().use { wallet ->
            val accounts = testAccounts(wallet, true)
            val accountIdx = accounts.size
            val subaddressIdx = accounts[accountIdx - 1].subaddresses!!.size
            val address = wallet.getAddress(accountIdx, subaddressIdx)
            assertNotNull(address)
            assertTrue(address.isNotEmpty())
        }
    }

    @Test
    fun getAccount_ReadsTheAccounts() {
        testWallet().use { wallet ->
            for (account in testAccounts(wallet)) {
                validateAddress(account.primaryAddress!!)
                assertTrue(account.subaddresses.isNullOrEmpty(), account.subaddresses.toString())
            }
        }
    }

    @Test
    fun getAccount_WithSubaddresses_IsNotSupported() {
        testWallet().use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getAccount(0, true) }
            assertEquals("monero_wallet_keys::get_account(account_idx, include_subaddresses) include_subaddresses must be false", error.message)
        }
    }

    @Test
    fun getSubaddresses_ReadsTheIndices() {
        testWallet().use { wallet ->
            for (accountIdx in accountIndices) {
                val subaddresses = wallet.getSubaddresses(accountIdx, subaddressIndices)
                assertEquals(subaddressIndices.size, subaddresses.size)
                for (subaddress in subaddresses) {
                    assertEquals(accountIdx, subaddress.accountIndex)
                    validateAddress(subaddress.address!!)
                }
            }
        }
    }

    @Test
    fun getSubaddressByIndex_MatchesThePluralCall() {
        testWallet().use { wallet ->
            for (accountIdx in accountIndices) {
                for (subaddress in wallet.getSubaddresses(accountIdx, subaddressIndices)) {
                    val byIndex = wallet.getSubaddress(accountIdx, subaddress.index!!)
                    assertEquals(subaddress.address, byIndex.address)
                    assertEquals(subaddress.accountIndex, byIndex.accountIndex)
                    assertEquals(subaddress.index, byIndex.index)
                    val singular = wallet.getSubaddresses(accountIdx, listOf(subaddress.index!!))[0]
                    assertEquals(subaddress.address, singular.address)
                }
            }
        }
    }

    @Test
    fun getSubaddresses_WithoutIndices_IsNotSupported() {
        testWallet().use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getSubaddresses(0, emptyList()) }
            assertTrue(error.message!!.contains("does not have enumerable set of subaddresses"), error.message)
        }
    }

    // ------------------------------- INTEGRATED ADDRESSES ----------------------------------

    @Test
    fun getIntegratedAddress_WithoutAStandardAddress_UsesThePrimaryAddress() {
        MoneroWalletKeys.createWalletRandom(config()).use { wallet ->
            val integrated = wallet.getIntegratedAddress("", "a".repeat(16))
            assertEquals(wallet.getPrimaryAddress(), integrated.standardAddress)
            assertEquals("a".repeat(16), integrated.paymentId)
        }
    }

    @Test
    fun getIntegratedAddress_WithAStandardAddress_ReadsItBack() {
        MoneroWalletKeys.createWalletRandom(config()).use { wallet ->
            val address = wallet.getPrimaryAddress()
            val integrated = wallet.getIntegratedAddress(address, "b".repeat(16))
            assertEquals(address, integrated.standardAddress)
            assertEquals("b".repeat(16), integrated.paymentId)
        }
    }

    @Test
    fun getIntegratedAddress_WithAnInvalidAddress_IsAnError() {
        MoneroWalletKeys.createWalletRandom(config()).use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress("notanaddress", "a".repeat(16)) }
            assertEquals("Invalid address", error.message)
        }
    }

    @Test
    fun getIntegratedAddress_WithASubaddress_IsAnError() {
        MoneroWalletKeys.createWalletRandom(config()).use { wallet ->
            val subaddress = wallet.getAddress(1, 1)
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(subaddress, "a".repeat(16)) }
            assertEquals("Subaddress shouldn't be used", error.message)
        }
    }

    @Test
    fun getIntegratedAddress_WithAnIntegratedAddress_IsAnError() {
        MoneroWalletKeys.createWalletRandom(config()).use { wallet ->
            val integrated = wallet.getIntegratedAddress("", "a".repeat(16)).integratedAddress
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(integrated, "a".repeat(16)) }
            assertEquals("Already integrated address", error.message)
        }
    }

    @Test
    fun getIntegratedAddress_WithoutAPaymentId_IsAnError() {
        MoneroWalletKeys.createWalletRandom(config()).use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(wallet.getPrimaryAddress(), "") }
            assertEquals("Payment ID shouldn't be left unspecified", error.message)
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
    fun signAndVerifyMessages_WithTheSpendKey() {
        val message = "This is a super important message which needs to be signed and verified."
        testWallet().use { wallet ->
            val positions = listOf(Pair(0, 0), Pair(0, 1), Pair(1, 0))
            for ((accountIdx, subaddressIdx) in positions) {
                val signature = wallet.signMessage(message, SIGN_WITH_SPEND_KEY, accountIdx, subaddressIdx)
                val result = wallet.verifyMessage(message, wallet.getAddress(accountIdx, subaddressIdx), signature)
                assertEquals(true, result.isGood)
                assertEquals(SIGN_WITH_SPEND_KEY, result.signatureType)
                val wrongMessage = wallet.verifyMessage("a different message", wallet.getAddress(accountIdx, subaddressIdx), signature)
                assertEquals(false, wrongMessage.isGood)
            }
        }
    }

    @Test
    fun verifyMessage_WithoutASignatureHeader_IsNotGood() {
        MoneroWalletKeys.createWalletRandom(config()).use { wallet ->
            val result = wallet.verifyMessage("hello", wallet.getPrimaryAddress(), "not-a-signature")
            assertEquals(false, result.isGood)
        }
    }

    @Test
    fun verifyMessage_ThatIsNotBase58_IsNotGood() {
        MoneroWalletKeys.createWalletRandom(config()).use { wallet ->
            val result = wallet.verifyMessage("hello", wallet.getPrimaryAddress(), "SigV2!!!not-base58!!!")
            assertEquals(false, result.isGood)
        }
    }

    @Test
    fun verifyMessage_WithATruncatedSignature_IsNotGood() {
        MoneroWalletKeys.createWalletRandom(config()).use { wallet ->
            val address = wallet.getPrimaryAddress()
            val signature = wallet.signMessage("hello", SIGN_WITH_SPEND_KEY, 0, 0)
            val truncated = signature.substring(0, 5) + signature.substring(5, signature.length - 10)
            val result = wallet.verifyMessage("hello", address, truncated)
            assertEquals(false, result.isGood)
        }
    }

    // ------------------------------- VERSION AND PAYMENT URIS ----------------------------------

    @Test
    fun getVersion_ReadsTheLibrary() {
        testWallet().use { wallet ->
            val version = wallet.getVersion()
            assertNotNull(version.number)
            assertTrue(version.number!! > 0)
            assertNotNull(version.isRelease)
        }
    }

    @Test
    fun getPaymentUri_RoundTripsAConfig() {
        testWallet().use { wallet ->
            val config = MoneroTxConfig()
            config.destinations = listOf(MoneroDestination(wallet.getAddress(0, 0), BigInteger.ZERO))
            val uri = wallet.getPaymentUri(config)
            assertSameJson(MoneroJson.serialize(config), MoneroJson.serialize(wallet.parsePaymentUri(uri)))

            val subaddress = wallet.getSubaddress(0, 1)
            config.destinations = listOf(MoneroDestination(subaddress.address, BigInteger.valueOf(425000000000L)))
            config.recipientName = "John Doe"
            config.note = "OMZG XMR FTW"
            val fullUri = wallet.getPaymentUri(config)
            assertSameJson(MoneroJson.serialize(config), MoneroJson.serialize(wallet.parsePaymentUri(fullUri)))

            config.destinations = listOf(MoneroDestination(null, BigInteger.ZERO))
            val error = assertFailsWith<MoneroError> { wallet.getPaymentUri(config) }
            // python matches the prefix of the message, whose reason names the missing parameter
            assertTrue(error.message!!.contains("Cannot make URI from supplied parameters"), error.message)
        }
    }

    // ------------------------------- LIFECYCLE ----------------------------------

    @Test
    fun isClosed_ThroughTheLifecycleOfAWallet() {
        val wallet = MoneroWalletKeys.createWalletRandom(config())
        assertFalse(wallet.isClosed())
        val address = wallet.getPrimaryAddress()

        wallet.close()
        assertTrue(wallet.isClosed())
        val closed = assertFailsWith<MoneroError> { wallet.getPrimaryAddress() }
        assertEquals("Wallet is closed", closed.message)
        val closedKey = assertFailsWith<MoneroError> { wallet.getPrivateViewKey() }
        assertEquals("Wallet is closed", closedKey.message)

        wallet.close()
        assertTrue(wallet.isClosed())
        validateAddress(address)
    }

    @Test
    fun closeWithASave_IsNotSupported() {
        val wallet = MoneroWalletKeys.createWalletRandom(config())
        val error = assertFailsWith<MoneroError> { wallet.close(true) }
        assertTrue(error.message!!.contains("does not support saving"), error.message)
        assertFalse(wallet.isClosed())
        wallet.close()
    }

    // ------------------------------- PRIVATE ------------------------------------

    private fun config(seed: String? = null): MoneroWalletConfig {
        val config = MoneroWalletConfig()
        config.networkType = MoneroNetworkType.MAINNET
        config.seed = seed
        return config
    }

    // the test wallet that the python suite shares as a class fixture, restored from its seed for every test, since
    // restoring a keys-only wallet is pure computation
    private fun testWallet(): MoneroWalletKeys {
        return MoneroWalletKeys.createWalletFromSeed(config(seed))
    }

    // the accounts that the python keys suite walks, with their subaddresses when they are asked for, which the
    // python suite assembles per account since a keys-only wallet cannot enumerate them
    private fun testAccounts(wallet: MoneroWalletKeys, includeSubaddresses: Boolean = false): List<MoneroAccount> {
        val accounts = accountIndices.map { accountIdx -> wallet.getAccount(accountIdx) }
        if (includeSubaddresses) {
            for ((i, accountIdx) in accountIndices.withIndex()) {
                accounts[i].subaddresses = wallet.getSubaddresses(accountIdx, subaddressIndices).toMutableList()
            }
        }
        return accounts
    }

    // python validates these with MoneroUtils, whose full surface is the utils PR of this repo; the checks here read
    // the same shape
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
}
