package monero.wallet

import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.ModelTestSupport.assertSameJson
import monero.NativeTestSupport
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroMessageSignatureType.SIGN_WITH_SPEND_KEY
import monero.wallet.model.MoneroTxConfig
import org.junit.jupiter.api.Tag
import kotlin.test.assertFalse
import kotlin.test.assertNull
import monero.wallet.model.MoneroAccount
import monero.wallet.model.MoneroSubaddress

/**
 * The tests that every wallet has to pass, whatever it is, like BaseTestMoneroWallet of monero-python (tests/test_monero_wallet_common.py):
 * they call the functions of {@link MoneroWallet} on a wallet that the subclass makes, so the full wallet and the keys-only wallet run
 * the same ones, and so will the wallet RPC. A subclass says how to make a wallet, a random one and the one of the test seed, how to close
 * it, and which languages and which default language the seed has.
 *
 * What only one kind of wallet does, such as a wallet file or a chain, is tested by its own class.
 *
 * Every test calls the real library, and skips when it is not found.
 */
@Tag("unit")
abstract class BaseMoneroWalletTest {

    // the test wallet of monero-python's config.ini, which its suites restore from the seed
    // the test wallet of monero-python's config.ini, which its keys suite restores from the seed
    protected val seed = "vortex degrees outbreak teeming gimmick school rounded tonic observant injury leech ought problems ahead upcoming ledge textbook cigar atrium trash dunes eavesdrop dullness evolved vortex"

    protected val primaryAddress = "48W9YHwPzRz9aPTeXCA6kmSpW6HsvmWx578jj3of2gT3JwZzwTf33amESBoNDkL6SVK34Q2HTKqgYbGyE1hBws3wCrcBDR2"

    protected val privateViewKey = "e8c2288181bad9ec410d7322efd65f663c6da57bd1d1198636278a039743a600"

    protected val privateSpendKey = "be7a2f71097f146bdf0fb5bb8edfe2240a9767e15adee74d95af1b5a64f29a0c"

    protected val publicViewKey = "42e465bdcd00de50516f1c7049bbe26bd3c11195e8dae5cceb38bad92d484269"

    protected val publicSpendKey = "b58d33a1dac23d334539cbed3657b69a5c967d6860357e24ab4d11899a312a6b"

    // the seed of monero-python's test_monero_wallet_common.ini, which is two words short of a mnemonic
    protected val brokenSeed = "memoir desk algebra inbound innocent unplugs fully okay five inflamed giant factual ritual toyed topic snake unhappy guarded tweezers haunted inundate giant"

    // the payment id of the python tests of integrated addresses
    protected val paymentId = "03284e41c342f036"

    /**
     * Make the test wallet, which is restored from [seed]. The test closes it.
     */
    protected abstract fun createTestWallet(): MoneroWallet

    /**
     * Make a wallet with a seed of its own. The test closes it.
     */
    protected abstract fun createRandomWallet(): MoneroWallet

    protected abstract fun closeWallet(wallet: MoneroWallet)

    /**
     * The languages that a seed can be written in, which are the ones of the wallet.
     */
    protected abstract fun seedLanguages(wallet: MoneroWallet): List<String>

    protected abstract val defaultLanguage: String

    @BeforeTest
    fun requireLibrary() {
        NativeTestSupport.assumeAvailable()
    }

    protected fun <T> withTestWallet(test: (MoneroWallet) -> T): T {
        val wallet = createTestWallet()
        try {
            return test(wallet)
        } finally {
            closeWallet(wallet)
        }
    }

    protected fun <T> withRandomWallet(test: (MoneroWallet) -> T): T {
        val wallet = createRandomWallet()
        try {
            return test(wallet)
        } finally {
            closeWallet(wallet)
        }
    }

    protected fun validateMnemonic(seed: String) {
        assertEquals(25, seed.split(" ").size, seed)
    }

    protected fun validatePrivateKey(key: String) {
        assertTrue(Regex("[0-9a-f]{64}").matches(key), key)
    }

    protected fun validateAddress(address: String) {
        // a standard address of mainnet starts with 4, a subaddress with 8, and both are 95 characters
        assertEquals(95, address.length, address)
        assertTrue(address.startsWith("4") || address.startsWith("8"), address)
    }

    // ------------------------------- THE TESTS ----------------------------------

    @Test
    fun getSeed_ReadsTheTestWallet() {
        withTestWallet { wallet ->
            val seed = wallet.getSeed()
            validateMnemonic(seed)
            assertEquals(this.seed, seed)
        }
    }

    @Test
    fun getSeedLanguage_IsTheDefaultLanguage() {
        withTestWallet { wallet ->
            assertEquals(defaultLanguage, wallet.getSeedLanguage())
        }
    }

    @Test
    fun getSeedLanguages_ListsTheLanguagesOfASeed() {
        withTestWallet { wallet ->
            val languages = seedLanguages(wallet)
            assertTrue(languages.isNotEmpty())
            for (language in languages) assertTrue(language.isNotEmpty())
            assertTrue(languages.contains(defaultLanguage), languages.toString())
        }
    }

    @Test
    fun getPrivateViewKey_ReadsTheTestWallet() {
        withTestWallet { wallet ->
            val key = wallet.getPrivateViewKey()
            validatePrivateKey(key)
            assertEquals(privateViewKey, key)
        }
    }

    @Test
    fun getPrivateSpendKey_ReadsTheTestWallet() {
        withTestWallet { wallet ->
            val key = wallet.getPrivateSpendKey()
            validatePrivateKey(key)
            assertEquals(privateSpendKey, key)
        }
    }

    @Test
    fun getPublicViewKey_ReadsTheTestWallet() {
        withTestWallet { wallet ->
            val key = wallet.getPublicViewKey()
            validatePrivateKey(key)
            assertEquals(publicViewKey, key)
        }
    }

    @Test
    fun getPublicSpendKey_ReadsTheTestWallet() {
        withTestWallet { wallet ->
            val key = wallet.getPublicSpendKey()
            validatePrivateKey(key)
            assertEquals(publicSpendKey, key)
        }
    }

    @Test
    fun getPrimaryAddress_ReadsTheTestWallet() {
        withTestWallet { wallet ->
            validateAddress(wallet.getPrimaryAddress())
            assertEquals(primaryAddress, wallet.getPrimaryAddress())
            assertEquals(primaryAddress, wallet.getAddress(0, 0))
        }
    }

    @Test
    fun getIntegratedAddress_WithoutAStandardAddress_UsesThePrimaryAddress() {
        withRandomWallet { wallet ->
            val integrated = wallet.getIntegratedAddress("", "a".repeat(16))
            assertEquals(wallet.getPrimaryAddress(), integrated.standardAddress)
            assertEquals("a".repeat(16), integrated.paymentId)
        }
    }

    @Test
    fun getIntegratedAddress_WithAStandardAddress_ReadsItBack() {
        withRandomWallet { wallet ->
            val address = wallet.getPrimaryAddress()
            val integrated = wallet.getIntegratedAddress(address, "b".repeat(16))
            assertEquals(address, integrated.standardAddress)
            assertEquals("b".repeat(16), integrated.paymentId)
        }
    }

    @Test
    fun getIntegratedAddress_WithAnInvalidAddress_IsAnError() {
        withRandomWallet { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress("notanaddress", "a".repeat(16)) }
            assertEquals("Invalid address", error.message)
        }
    }

    @Test
    fun getIntegratedAddress_WithASubaddress_IsAnError() {
        withRandomWallet { wallet ->
            val subaddress = wallet.getAddress(1, 1)
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(subaddress, "a".repeat(16)) }
            assertEquals("Subaddress shouldn't be used", error.message)
        }
    }

    @Test
    fun getIntegratedAddress_WithAnIntegratedAddress_IsAnError() {
        withRandomWallet { wallet ->
            val integrated = wallet.getIntegratedAddress("", "a".repeat(16)).integratedAddress
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(integrated, "a".repeat(16)) }
            assertEquals("Already integrated address", error.message)
        }
    }

    @Test
    fun getIntegratedAddress_WithoutAPaymentId_IsAnError() {
        withRandomWallet { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(wallet.getPrimaryAddress(), "") }
            assertEquals("Payment ID shouldn't be left unspecified", error.message)
        }
    }

    @Test
    fun decodeIntegratedAddress_ReadsItBack() {
        withTestWallet { wallet ->
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

    @Test
    fun verifyMessage_WithoutASignatureHeader_IsNotGood() {
        withRandomWallet { wallet ->
            val result = wallet.verifyMessage("hello", wallet.getPrimaryAddress(), "not-a-signature")
            assertEquals(false, result.isGood)
        }
    }

    @Test
    fun verifyMessage_ThatIsNotBase58_IsNotGood() {
        withRandomWallet { wallet ->
            val result = wallet.verifyMessage("hello", wallet.getPrimaryAddress(), "SigV2!!!not-base58!!!")
            assertEquals(false, result.isGood)
        }
    }

    @Test
    fun verifyMessage_WithATruncatedSignature_IsNotGood() {
        withRandomWallet { wallet ->
            val address = wallet.getPrimaryAddress()
            val signature = wallet.signMessage("hello", SIGN_WITH_SPEND_KEY, 0, 0)
            val truncated = signature.substring(0, 5) + signature.substring(5, signature.length - 10)
            val result = wallet.verifyMessage("hello", address, truncated)
            assertEquals(false, result.isGood)
        }
    }

    @Test
    fun getVersion_ReadsTheLibrary() {
        withTestWallet { wallet ->
            val version = wallet.getVersion()
            assertNotNull(version.number)
            assertTrue(version.number!! > 0)
            assertNotNull(version.isRelease)
        }
    }

    @Test
    fun getPaymentUri_RoundTripsAConfig() {
        withTestWallet { wallet ->
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

    // ------------------------------- ACCOUNTS AND SUBADDRESSES ----------------------------------

    protected fun validateSubaddress(subaddress: MoneroSubaddress, full: Boolean = true) {
        assertNotNull(subaddress.accountIndex)
        assertNotNull(subaddress.index)
        assertTrue(subaddress.accountIndex!! >= 0)
        assertTrue(subaddress.index!! >= 0)
        assertFalse(subaddress.address.isNullOrEmpty())
        assertTrue(subaddress.label == null || subaddress.label != "", "the label is ${subaddress.label}")
        if (full) {
            assertNotNull(subaddress.balance)
            assertNotNull(subaddress.unlockedBalance)
            assertTrue(subaddress.balance!!.signum() >= 0)
            assertTrue(subaddress.unlockedBalance!!.signum() >= 0)
            assertTrue(subaddress.numUnspentOutputs!! >= 0)
            assertNotNull(subaddress.isUsed)
            if (subaddress.balance!!.signum() > 0) assertTrue(subaddress.isUsed!!)
            assertTrue(subaddress.numBlocksToUnlock!! >= 0)
        }
    }

    protected fun validateAccount(account: MoneroAccount, full: Boolean = true) {
        assertNotNull(account.index)
        assertTrue(account.index!! >= 0)
        validateAddress(account.primaryAddress!!)
        if (full) {
            assertNotNull(account.balance)
            assertNotNull(account.unlockedBalance)
            assertTrue(account.balance!!.signum() >= 0)
            assertTrue(account.unlockedBalance!!.signum() >= 0)

            // the balances of the subaddresses add up to the ones of the account
            val subaddresses = account.subaddresses
            if (!subaddresses.isNullOrEmpty()) {
                var balance = BigInteger.ZERO
                var unlockedBalance = BigInteger.ZERO
                for ((i, subaddress) in subaddresses.withIndex()) {
                    validateSubaddress(subaddress)
                    assertEquals(account.index, subaddress.accountIndex)
                    assertEquals(i, subaddress.index)
                    balance = balance.add(subaddress.balance!!)
                    unlockedBalance = unlockedBalance.add(subaddress.unlockedBalance!!)
                }
                assertEquals(account.balance, balance, "The balances of the subaddresses are not the one of the account ${account.index}")
                assertEquals(account.unlockedBalance, unlockedBalance, "The unlocked balances of the subaddresses are not the one of the account ${account.index}")
            }
        }
        assertTrue(account.tag == null || account.tag != "")
    }

    // Can get accounts without subaddresses
    @Test
    open fun getAccounts_WithoutSubaddresses() {
        withTestWallet { wallet ->
            val accounts = wallet.getAccounts()
            assertTrue(accounts.isNotEmpty())
            for (account in accounts) {
                validateAccount(account)
                assertTrue(account.subaddresses.isNullOrEmpty())
            }
        }
    }

    // Can get accounts with subaddresses
    @Test
    open fun getAccounts_WithSubaddresses() {
        withTestWallet { wallet ->
            val accounts = wallet.getAccounts(true)
            assertTrue(accounts.isNotEmpty())
            for (account in accounts) {
                validateAccount(account)
                assertTrue(account.subaddresses!!.isNotEmpty())
            }
        }
    }

    // Can create an account without a label
    @Test
    open fun createAccount_WithoutALabel() {
        withTestWallet { wallet ->
            val accountsBefore = wallet.getAccounts()
            val created = wallet.createAccount()
            validateAccount(created)
            assertEquals(accountsBefore.size, wallet.getAccounts().size - 1)
            assertEquals(accountsBefore.size, created.index)
        }
    }

    // Can create an account with a label
    @Test
    open fun createAccount_WithALabel() {
        withTestWallet { wallet ->
            val accountsBefore = wallet.getAccounts()
            val label = java.util.UUID.randomUUID().toString()
            var created = wallet.createAccount(label)
            validateAccount(created)
            assertEquals(accountsBefore.size, wallet.getAccounts().size - 1)
            assertEquals(label, wallet.getSubaddress(created.index!!, 0).label)
            validateAccount(wallet.getAccount(created.index!!))

            // an account with the same label
            created = wallet.createAccount(label)
            validateAccount(created)
            assertEquals(accountsBefore.size, wallet.getAccounts().size - 2)
            assertEquals(label, wallet.getSubaddress(created.index!!, 0).label)
            validateAccount(wallet.getAccount(created.index!!))
        }
    }

    // Can set account labels
    @Test
    open fun setAccountLabel_SetsTheLabelOfTheFirstSubaddress() {
        withTestWallet { wallet ->
            if (wallet.getAccounts().size < 2) wallet.createAccount()
            val label = java.util.UUID.randomUUID().toString()
            wallet.setAccountLabel(1, label)
            assertEquals(label, wallet.getSubaddress(1, 0).label)
        }
    }

    // Can get the subaddresses of an account
    @Test
    open fun getSubaddresses_OfEveryAccount() {
        withTestWallet { wallet ->
            val accounts = wallet.getAccounts()
            assertTrue(accounts.isNotEmpty())
            for (account in accounts) {
                val subaddresses = wallet.getSubaddresses(account.index!!)
                assertTrue(subaddresses.isNotEmpty())
                for (subaddress in subaddresses) {
                    validateSubaddress(subaddress)
                    assertEquals(account.index, subaddress.accountIndex)
                }
            }
        }
    }

    // Can get the subaddresses of an account at some indices
    @Test
    open fun getSubaddresses_ByIndices() {
        withTestWallet { wallet ->
            for (account in wallet.getAccounts()) {
                var subaddresses = wallet.getSubaddresses(account.index!!)
                assertTrue(subaddresses.isNotEmpty())

                // leave out the first one, if there is another
                if (subaddresses.size > 1) subaddresses = subaddresses.drop(1)
                val indices = subaddresses.map { it.index!! }
                assertTrue(indices.isNotEmpty())

                val fetched = wallet.getSubaddresses(account.index!!, indices)
                assertSameJson(MoneroJson.serialize(subaddresses), MoneroJson.serialize(fetched))
            }
        }
    }

    // Can create a subaddress with and without a label
    @Test
    open fun createSubaddress_WithAndWithoutALabel() {
        withTestWallet { wallet ->
            if (wallet.getAccounts().size < 2) wallet.createAccount()
            assertTrue(wallet.getAccounts().size > 1)
            for (accountIdx in 0..1) {

                // without a label
                var subaddresses = wallet.getSubaddresses(accountIdx)
                var created = wallet.createSubaddress(accountIdx)
                assertNull(created.label)
                validateSubaddress(created)
                var subaddressesNew = wallet.getSubaddresses(accountIdx)
                assertEquals(subaddresses.size, subaddressesNew.size - 1)
                assertSameJson(MoneroJson.serialize(created), MoneroJson.serialize(subaddressesNew.last()))

                // with a label
                subaddresses = wallet.getSubaddresses(accountIdx)
                val label = java.util.UUID.randomUUID().toString()
                created = wallet.createSubaddress(accountIdx, label)
                assertEquals(label, created.label)
                validateSubaddress(created)
                subaddressesNew = wallet.getSubaddresses(accountIdx)
                assertEquals(subaddresses.size, subaddressesNew.size - 1)
                assertSameJson(MoneroJson.serialize(created), MoneroJson.serialize(subaddressesNew.last()))
            }
        }
    }

    // Can set subaddress labels
    @Test
    open fun setSubaddressLabel_SetsTheLabelsOfTheSubaddresses() {
        withTestWallet { wallet ->
            while (wallet.getSubaddresses(0).size < 3) wallet.createSubaddress(0)
            for (subaddressIdx in wallet.getSubaddresses(0).indices) {
                val label = java.util.UUID.randomUUID().toString()
                wallet.setSubaddressLabel(0, subaddressIdx, label)
                assertEquals(label, wallet.getSubaddress(0, subaddressIdx).label)
            }
        }
    }

    // Can get the account and the subaddress of an address
    @Test
    open fun getAddressIndex_OfTheAddressesOfTheWallet() {
        withTestWallet { wallet ->
            val accounts = wallet.getAccounts(true)
            val accountIdx = accounts.size - 1
            val subaddressIdx = accounts[accountIdx].subaddresses!!.size - 1
            val address = wallet.getAddress(accountIdx, subaddressIdx)
            assertTrue(address.isNotEmpty())

            val subaddress = wallet.getAddressIndex(address)
            assertEquals(accountIdx, subaddress.accountIndex)
            assertEquals(subaddressIdx, subaddress.index)

            // an address that is valid and is not the wallet's
            val notFound = assertFailsWith<MoneroError> { wallet.getAddressIndex("49cvU1JnXFAH7r1RbDLJw78aaDnhW4sCKRbLaTYa9eHvcz9PK1YXwod5npWZvMyQ8L4waVjUhuCp6btFyELkRpA4SWNKeQH") }
            assertEquals("Address doesn't belong to the wallet", notFound.message)

            // an address that is not one
            val invalid = assertFailsWith<MoneroError> { wallet.getAddressIndex("this is definitely not an address") }
            assertEquals("Invalid address", invalid.message)
        }
    }
}
