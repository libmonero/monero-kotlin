package monero.wallet

import org.junit.jupiter.api.Tag
import monero.NotSupported
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.common.MoneroError
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroAccount
import monero.wallet.model.MoneroMessageSignatureType.SIGN_WITH_SPEND_KEY
import monero.wallet.model.MoneroWalletConfig

/**
 * Mirrors the keys-only wallet tests of monero-python (tests/test_monero_wallet_keys.py over
 * test_monero_wallet_common.py), which run without a daemon: a keys-only wallet has the keys, the seed and the
 * addresses, and nothing else, so the python suite marks the 88 tests that need a chain or a wallet file as not
 * supported and runs the rest against the account of its test seed.
 *
 * What every wallet has to pass, such as the keys, the seed, the addresses and the messages, is in [BaseMoneroWalletTest], which this
 * class extends.
 *
 * Every test calls the real dylib through MoneroWalletKeys, and skips when it is not found. The python suite
 * validates what it reads with MoneroUtils, whose full surface is the utils PR of this repo; these tests check the
 * same shape with their own validators. Two python tests are not mirrored, which the report of this work lists: an
 * invalid signature type cannot be written in Kotlin, whose enumeration has no invalid value, and the errors of the
 * python binding's own not-supported surface, which this class does not expose at all.
 */
@Tag("unit")
class MoneroWalletKeysTest : BaseMoneroWalletTest() {

    // the accounts and subaddresses that the python keys suite walks
    private val accountIndices = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    private val subaddressIndices = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    override fun createTestWallet(): MoneroWallet = testWallet()

    override fun createRandomWallet(): MoneroWallet = MoneroWalletKeys.createWalletRandom(config())

    override fun closeWallet(wallet: MoneroWallet) = (wallet as MoneroWalletKeys).close()

    override fun seedLanguages(wallet: MoneroWallet): List<String> = (wallet as MoneroWalletKeys).getSeedLanguages()

    override val defaultLanguage: String = MoneroWalletKeys.DEFAULT_LANGUAGE

    // ------------------------------- CREATE WALLET ----------------------------------

    @Test
    fun testCreateWalletRandom() {
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
    fun testCreateWalletRandomWithUnknownLanguage() {
        val config = config()
        config.language = "english"
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletRandom(config) }
        assertEquals("Unknown language: english", error.message)
    }

    @Test
    fun testCreateWalletFromSeed() {
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
    fun testCreateWalletFromSeedWithBrokenSeed() {
        val config = config(brokenSeed)
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromSeed(config) }
        assertEquals("Invalid mnemonic", error.message)
    }

    @Test
    fun testCreateWalletFromSeedWithOffset() {
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
    fun testCreateWalletFromKeys() {
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
    fun testCreateWalletFromKeysWithSpendKey() {
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
    fun testCreateWalletFromKeysWithViewKey() {
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
    fun testCreateWalletFromKeysNoKeys() {
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromKeys(config()) }
        assertEquals("Neither spend key nor view key supplied", error.message)
    }

    @Test
    fun testCreateWalletFromKeysInvalidSpendKey() {
        val config = config()
        config.privateSpendKey = "not-a-valid-hex-secret-key"
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromKeys(config) }
        assertEquals("failed to parse secret spend key", error.message)
    }

    @Test
    fun testCreateWalletFromKeysInvalidViewKey() {
        val config = config()
        config.primaryAddress = primaryAddress
        config.privateViewKey = "not-a-valid-hex-secret-key"
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromKeys(config) }
        assertEquals("failed to parse secret view key", error.message)
    }

    @Test
    fun testCreateWalletFromKeysViewKeyWithoutAddress() {
        val config = config()
        config.privateViewKey = "a".repeat(64)
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromKeys(config) }
        assertEquals("must provide address if providing private view key", error.message)
    }

    @Test
    fun testCreateWalletWithoutNetworkType() {
        val config = MoneroWalletConfig()
        config.seed = seed
        val error = assertFailsWith<MoneroError> { MoneroWalletKeys.createWalletFromSeed(config) }
        assertEquals("Must provide wallet network type", error.message)
    }

    // ------------------------------- SEED AND KEYS ----------------------------------

    // The list of the languages is the one of monero-c, so a wallet that is closed lists them too
    @Test
    fun testGetSeedLanguagesOfClosedWallet() {
        val wallet = MoneroWalletKeys.createWalletRandom(config())
        wallet.close()

        assertTrue(wallet.getSeedLanguages().contains(MoneroWalletKeys.DEFAULT_LANGUAGE))
    }

    // ------------------------------- ADDRESSES, ACCOUNTS AND SUBADDRESSES ----------------------------------

    @Test
    fun testGetSubaddressAddress() {
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
    fun testGetSubaddressAddressOutOfRange() {
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
    fun testGetAccount() {
        testWallet().use { wallet ->
            for (account in testAccounts(wallet)) {
                validateAddress(account.primaryAddress!!)
                assertTrue(account.subaddresses.isNullOrEmpty(), account.subaddresses.toString())
            }
        }
    }

    @Test
    fun testGetAccountWithSubaddresses() {
        testWallet().use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getAccount(0, true) }
            assertEquals("monero_wallet_keys::get_account(account_idx, include_subaddresses) include_subaddresses must be false", error.message)
        }
    }

    @Test
    fun testGetSubaddressesByIndicesOfAllAccounts() {
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
    fun testGetSubaddressByIndex() {
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
    fun testGetSubaddressesWithEmptyIndices() {
        testWallet().use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getSubaddresses(0, emptyList()) }
            assertTrue(error.message!!.contains("does not have enumerable set of subaddresses"), error.message)
        }
    }

    // ------------------------------- INTEGRATED ADDRESSES ----------------------------------

    // ------------------------------- MESSAGES ----------------------------------

    @Test
    fun testSignAndVerifyMessagesWithSpendKey() {
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

    // ------------------------------- VERSION AND PAYMENT URIS ----------------------------------

    // ------------------------------- LIFECYCLE ----------------------------------

    @Test
    fun testIsClosed() {
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
    fun testCloseWithSaveNotSupported() {
        val wallet = MoneroWalletKeys.createWalletRandom(config())
        val error = assertFailsWith<MoneroError> { wallet.close(true) }
        assertTrue(error.message!!.contains("does not support saving"), error.message)
        assertFalse(wallet.isClosed())
        wallet.close()
    }

    // Threads that close the same wallet at once close it once: a second close of the native handle is an error, and
    // freeing it twice is worse
    @Test
    fun testCloseFromManyThreads() {
        repeat(5) {
            val wallet = MoneroWalletKeys.createWalletRandom(config())
            val threads = 8
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(threads)
            try {
                val closes = (0 until threads).map {
                    pool.submit {
                        start.await()
                        wallet.close()
                    }
                }
                start.countDown()
                for (close in closes) close.get(30, TimeUnit.SECONDS)
            } finally {
                pool.shutdownNow()
            }
            assertTrue(wallet.isClosed())
        }
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

    // ------------------------------- THE INTERFACE ----------------------------------

    @Test
    fun testIsMoneroWallet() {
        testWallet().use { wallet ->
            val asInterface: MoneroWallet = wallet
            assertEquals(primaryAddress, asInterface.getPrimaryAddress())
            assertEquals(seed, asInterface.getSeed())
            assertTrue(asInterface is MoneroWalletDefault)
        }
    }

    // a keys-only wallet has no chain, no daemon and no file, so what needs one is not supported
    @Test
    fun testFunctionsThatNeedChainOrFileNotSupported() {
        testWallet().use { wallet ->
            val calls = mapOf<String, () -> Unit>(
                "getHeight" to { wallet.getHeight() },
                "isSynced" to { wallet.isSynced() },
                "getBalance" to { wallet.getBalance() },
                "getUnlockedBalance" to { wallet.getUnlockedBalance() },
                "sync" to { wallet.sync() },
                "getTxs" to { wallet.getTxs() },
                "getTransfers" to { wallet.getTransfers() },
                "getOutputs" to { wallet.getOutputs() },
                "getPath" to { wallet.getPath() },
                "save" to { wallet.save() },
                "getDaemonConnection" to { wallet.getDaemonConnection() },
                "setDaemonConnection" to { wallet.setDaemonConnection("http://127.0.0.1:18081") },
                "startSyncing" to { wallet.startSyncing() },
                "createAccount" to { wallet.createAccount() }
            )
            for ((name, call) in calls) {
                val error = assertFailsWith<MoneroError>(name) { call() }
                assertTrue("not supported" in error.message!!, "$name: ${error.message}")
            }
        }
    }

    @Test
    fun testGetSubaddressesWithoutIndices() {
        testWallet().use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getSubaddresses(0) }
            assertTrue("not supported" in error.message!!, error.message)
        }
    }

    @Test
    fun testGetSubaddressesByIndicesOfFirstAccount() {
        testWallet().use { wallet ->
            val subaddresses = wallet.getSubaddresses(0, listOf(0, 1))
            assertEquals(listOf(0, 1), subaddresses.map { it.index })
            assertEquals(primaryAddress, subaddresses[0].address)
        }
    }

    // ------------------------------- WHAT A KEYS-ONLY WALLET CAN NOT DO ----------------------------------

    // the accounts and the subaddresses are made and read by a wallet that can enumerate them, which a keys-only wallet can not

    @NotSupported
    @Test
    override fun testGetAccountsWithoutSubaddresses() = super.testGetAccountsWithoutSubaddresses()

    @NotSupported
    @Test
    override fun testGetAccountsWithSubaddresses() = super.testGetAccountsWithSubaddresses()

    @NotSupported
    @Test
    override fun testCreateAccountWithoutLabel() = super.testCreateAccountWithoutLabel()

    @NotSupported
    @Test
    override fun testCreateAccountWithLabel() = super.testCreateAccountWithLabel()

    @NotSupported
    @Test
    override fun testSetAccountLabel() = super.testSetAccountLabel()

    @NotSupported
    @Test
    override fun testGetSubaddresses() = super.testGetSubaddresses()

    @NotSupported
    @Test
    override fun testGetSubaddressesByIndices() = super.testGetSubaddressesByIndices()

    @NotSupported
    @Test
    override fun testCreateSubaddress() = super.testCreateSubaddress()

    @NotSupported
    @Test
    override fun testSetSubaddressLabel() = super.testSetSubaddressLabel()

    @NotSupported
    @Test
    override fun testGetAddressIndices() = super.testGetAddressIndices()


    @NotSupported
    @Test
    override fun testGetAllBalances() = super.testGetAllBalances()
}
