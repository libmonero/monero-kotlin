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
import monero.wallet.model.MoneroMessageSignatureResult
import monero.wallet.model.MoneroMessageSignatureType.SIGN_WITH_SPEND_KEY
import monero.wallet.model.MoneroMessageSignatureType.SIGN_WITH_VIEW_KEY
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxPriority
import kotlin.test.assertFalse
import kotlin.test.assertNull
import monero.wallet.model.MoneroAccount
import monero.wallet.model.MoneroAddressBookEntry
import monero.wallet.model.MoneroSubaddress

/**
 * The tests that every wallet has to pass, whatever it is, like BaseTestMoneroWallet of monero-python (tests/test_monero_wallet_common.py):
 * they call the functions of {@link MoneroWallet} on a wallet that the subclass makes, so the full wallet and the keys-only wallet run
 * the same ones, and so will the wallet RPC. A subclass says how to make a wallet, a random one and the one of the test seed, how to close
 * it, and which languages and which default language the seed has.
 *
 * What only one kind of wallet does, such as a wallet file or a chain, is tested by its own class.
 *
 * The class has no tag: the tests that a subclass runs are unit tests or integration tests according to the wallet that it makes, so the
 * subclass is tagged (TestTagsTest). Every test calls the real library, and skips when it is not found.
 */
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
     * Whether verifying a message for an address that is not one is an error: wallet2 refuses the address, and monero-wallet-rpc answers that
     * the signature is not good, which is what monero-python sees.
     */
    protected open val verifyMessageWithInvalidAddressThrows: Boolean = true

    /**
     * The password of the files of the wallets that [createRandomWallet] makes.
     */
    protected abstract val walletPassword: String

    /**
     * Open the wallet that a closed wallet left at its path. The test closes it.
     *
     * @param path is the path of the wallet, which [MoneroWallet.getPath] says
     * @param password is the password of the files of the wallet
     */
    protected abstract fun openWallet(path: String, password: String): MoneroWallet

    /**
     * The languages that a seed can be written in, which are the ones of the wallet.
     */
    protected abstract fun seedLanguages(wallet: MoneroWallet): List<String>

    protected abstract val defaultLanguage: String

    /**
     * A subaddress of a wallet that a test makes with [createRandomWallet]. A wallet that computes the addresses of its subaddresses
     * has one at any index, and a wallet of a server has the ones that were created.
     */
    protected open fun subaddressOf(wallet: MoneroWallet): String = wallet.getAddress(1, 1)

    /**
     * Whether an integrated address that is asked for without a payment id gets one that the wallet makes, which a server does, and not
     * an error, which a wallet of the library does.
     */
    protected open val makesAPaymentId: Boolean = false

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
    fun testGetSeed() {
        withTestWallet { wallet ->
            val seed = wallet.getSeed()
            validateMnemonic(seed)
            assertEquals(this.seed, seed)
        }
    }

    @Test
    open fun testGetSeedLanguage() {
        withTestWallet { wallet ->
            assertEquals(defaultLanguage, wallet.getSeedLanguage())
        }
    }

    @Test
    fun testGetSeedLanguages() {
        withTestWallet { wallet ->
            val languages = seedLanguages(wallet)
            assertTrue(languages.isNotEmpty())
            for (language in languages) assertTrue(language.isNotEmpty())
            assertTrue(languages.contains(defaultLanguage), languages.toString())
        }
    }

    @Test
    fun testGetPrivateViewKey() {
        withTestWallet { wallet ->
            val key = wallet.getPrivateViewKey()
            validatePrivateKey(key)
            assertEquals(privateViewKey, key)
        }
    }

    @Test
    fun testGetPrivateSpendKey() {
        withTestWallet { wallet ->
            val key = wallet.getPrivateSpendKey()
            validatePrivateKey(key)
            assertEquals(privateSpendKey, key)
        }
    }

    @Test
    open fun testGetPublicViewKey() {
        withTestWallet { wallet ->
            val key = wallet.getPublicViewKey()
            validatePrivateKey(key)
            assertEquals(publicViewKey, key)
        }
    }

    @Test
    open fun testGetPublicSpendKey() {
        withTestWallet { wallet ->
            val key = wallet.getPublicSpendKey()
            validatePrivateKey(key)
            assertEquals(publicSpendKey, key)
        }
    }

    @Test
    fun testGetPrimaryAddress() {
        withTestWallet { wallet ->
            validateAddress(wallet.getPrimaryAddress())
            assertEquals(primaryAddress, wallet.getPrimaryAddress())
            assertEquals(primaryAddress, wallet.getAddress(0, 0))
        }
    }

    @Test
    fun testGetIntegratedAddressWithoutStandardAddress() {
        withRandomWallet { wallet ->
            val integrated = wallet.getIntegratedAddress("", "a".repeat(16))
            assertEquals(wallet.getPrimaryAddress(), integrated.standardAddress)
            assertEquals("a".repeat(16), integrated.paymentId)
        }
    }

    @Test
    fun testGetIntegratedAddressWithStandardAddress() {
        withRandomWallet { wallet ->
            val address = wallet.getPrimaryAddress()
            val integrated = wallet.getIntegratedAddress(address, "b".repeat(16))
            assertEquals(address, integrated.standardAddress)
            assertEquals("b".repeat(16), integrated.paymentId)
        }
    }

    @Test
    fun testGetIntegratedAddressWithInvalidAddress() {
        withRandomWallet { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress("notanaddress", "a".repeat(16)) }
            assertEquals("Invalid address", error.message)
        }
    }

    @Test
    fun testGetIntegratedAddressWithSubaddress() {
        withRandomWallet { wallet ->
            val subaddress = subaddressOf(wallet)
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(subaddress, "a".repeat(16)) }
            assertEquals("Subaddress shouldn't be used", error.message)
        }
    }

    @Test
    fun testGetIntegratedAddressWithIntegratedAddress() {
        withRandomWallet { wallet ->
            val integrated = wallet.getIntegratedAddress("", "a".repeat(16)).integratedAddress
            val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(integrated, "a".repeat(16)) }
            assertEquals("Already integrated address", error.message)
        }
    }

    @Test
    fun testGetIntegratedAddressWithoutPaymentId() {
        withRandomWallet { wallet ->
            if (makesAPaymentId) {
                val integrated = wallet.getIntegratedAddress(wallet.getPrimaryAddress(), "")
                assertEquals(wallet.getPrimaryAddress(), integrated.standardAddress)
                assertTrue(Regex("[0-9a-f]{16}").matches(integrated.paymentId!!), integrated.paymentId)
            } else {
                val error = assertFailsWith<MoneroError> { wallet.getIntegratedAddress(wallet.getPrimaryAddress(), "") }
                assertEquals("Payment ID shouldn't be left unspecified", error.message)
            }
        }
    }

    @Test
    fun testDecodeIntegratedAddress() {
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

    // Can sign a message with the spend key and with the view key, for the first subaddresses, and verify it: not for another address,
    // not for what is not an address, and not for an address of another wallet (the mining wallet of monero-python's config.ini)
    @Test
    open fun testSignAndVerifyMessages() {
        val message = "This is a super important message which needs to be signed and verified."
        val externalAddress = "49cvU1JnXFAH7r1RbDLJw78aaDnhW4sCKRbLaTYa9eHvcz9PK1YXwod5npWZvMyQ8L4waVjUhuCp6btFyELkRpA4SWNKeQH"
        withTestWallet { wallet ->
            fun assertResult(result: MoneroMessageSignatureResult, good: Boolean) {
                assertEquals(good, result.isGood)
                assertEquals(false, result.isOld)
                if (good) assertEquals(2, result.version)
            }

            for ((accountIdx, subaddressIdx) in listOf(Pair(0, 0), Pair(0, 1), Pair(1, 0))) {
                for (signatureType in listOf(SIGN_WITH_SPEND_KEY, SIGN_WITH_VIEW_KEY)) {
                    val address = wallet.getAddress(accountIdx, subaddressIdx)
                    val signature = wallet.signMessage(message, signatureType, accountIdx, subaddressIdx)

                    val good = wallet.verifyMessage(message, address, signature)
                    assertResult(good, true)
                    assertEquals(signatureType, good.signatureType)
                    assertEquals(false, wallet.verifyMessage("a different message", address, signature).isGood)

                    assertResult(wallet.verifyMessage(message, wallet.getAddress(0, 2), signature), false)
                    if (verifyMessageWithInvalidAddressThrows) {
                        assertFailsWith<MoneroError> { wallet.verifyMessage(message, "invalid address", signature) }
                    } else {
                        assertResult(wallet.verifyMessage(message, "invalid address", signature), false)
                    }
                    assertResult(wallet.verifyMessage(message, externalAddress, signature), false)
                }
            }
        }
    }

    // Can change the password of a wallet with files, which is written with the new one at once: the old one does not open it, the new one
    // does, and the old password has to be the right one to change it
    @Test
    open fun testChangePassword() {
        val newPassword = ""
        val wallet = createRandomWallet()
        val path: String
        try {
            path = wallet.getPath()
            wallet.changePassword(walletPassword, newPassword)
        } finally {
            closeWallet(wallet)
        }

        // the old password does not work, and the change was written without a save: wallet2 and the server say it in their own words
        val error = assertFailsWith<MoneroError> { openWallet(path, walletPassword) }
        val message = error.message!!.lowercase()
        assertTrue("failed to open wallet" in message || "password" in message, error.message)

        val reopened = openWallet(path, newPassword)
        try {
            assertEquals("Invalid original password.", assertFailsWith<MoneroError> { reopened.changePassword("badpassword", newPassword) }.message)
            reopened.save()
        } finally {
            closeWallet(reopened)
        }

        closeWallet(openWallet(path, newPassword))
    }

    // Can save and close the wallet in a single call: what was set and not saved is lost with a close, and kept with a close that saves.
    // monero-python gets an empty string for an attribute that was not set, here it is null
    @Test
    open fun testSaveAndClose() {
        val uuid = java.util.UUID.randomUUID().toString()
        val wallet = createRandomWallet()
        val path: String
        try {
            path = wallet.getPath()
            wallet.setAttribute("id", uuid)
        } catch (e: Throwable) {
            closeWallet(wallet)
            throw e
        }

        // closed without saving: the attribute is not there when the wallet opens again
        wallet.close(false)
        val reopened = openWallet(path, walletPassword)
        try {
            assertNull(reopened.getAttribute("id"))
            reopened.setAttribute("id", uuid)
        } catch (e: Throwable) {
            closeWallet(reopened)
            throw e
        }

        // closed with a save: it is
        reopened.close(true)
        val saved = openWallet(path, walletPassword)
        try {
            assertEquals(uuid, saved.getAttribute("id"))
        } finally {
            closeWallet(saved)
        }
    }

    @Test
    fun testVerifyMessageWithoutSignatureHeader() {
        withRandomWallet { wallet ->
            val result = wallet.verifyMessage("hello", wallet.getPrimaryAddress(), "not-a-signature")
            assertEquals(false, result.isGood)
        }
    }

    @Test
    fun testVerifyMessageNotBase58() {
        withRandomWallet { wallet ->
            val result = wallet.verifyMessage("hello", wallet.getPrimaryAddress(), "SigV2!!!not-base58!!!")
            assertEquals(false, result.isGood)
        }
    }

    @Test
    fun testVerifyMessageWithTruncatedSignature() {
        withRandomWallet { wallet ->
            val address = wallet.getPrimaryAddress()
            val signature = wallet.signMessage("hello", SIGN_WITH_SPEND_KEY, 0, 0)
            val truncated = signature.substring(0, 5) + signature.substring(5, signature.length - 10)
            val result = wallet.verifyMessage("hello", address, truncated)
            assertEquals(false, result.isGood)
        }
    }

    // Can set attributes and get them back. monero-python answers an empty string for an attribute that was not set, here it is null
    @Test
    open fun testSetAttributes() {
        withTestWallet { wallet ->
            val attributes = (0 until 5).associate { "attr$it" to java.util.UUID.randomUUID().toString() }
            attributes.forEach { (key, value) -> wallet.setAttribute(key, value) }
            attributes.forEach { (key, value) -> assertEquals(value, wallet.getAttribute(key)) }
            assertNull(wallet.getAttribute("unset_key"))
        }
    }

    // Can get the default priority of the fee, which is not DEFAULT: that one means to use the default
    @Test
    open fun testGetDefaultFeePriority() {
        withTestWallet { wallet ->
            assertTrue(wallet.getDefaultFeePriority() != MoneroTxPriority.DEFAULT)
        }
    }

    @Test
    fun testGetVersion() {
        withTestWallet { wallet ->
            val version = wallet.getVersion()
            assertNotNull(version.number)
            assertTrue(version.number!! > 0)
            assertNotNull(version.isRelease)
        }
    }

    @Test
    fun testGetPaymentUri() {
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
    open fun testGetAccountsWithoutSubaddresses() {
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
    open fun testGetAccountsWithSubaddresses() {
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
    open fun testCreateAccountWithoutLabel() {
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
    open fun testCreateAccountWithLabel() {
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
    open fun testSetAccountLabel() {
        withTestWallet { wallet ->
            if (wallet.getAccounts().size < 2) wallet.createAccount()
            val label = java.util.UUID.randomUUID().toString()
            wallet.setAccountLabel(1, label)
            assertEquals(label, wallet.getSubaddress(1, 0).label)
        }
    }

    // Can get the subaddresses of an account
    @Test
    open fun testGetSubaddresses() {
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
    open fun testGetSubaddressesByIndices() {
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
    open fun testCreateSubaddress() {
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
    open fun testSetSubaddressLabel() {
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
    open fun testGetAddressIndices() {
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

    // Can get the balances of the wallet, of its accounts and of their subaddresses, which add up
    @Test
    open fun testGetAllBalances() {
        withTestWallet { wallet ->
            if (wallet.getAccounts().size < 2) wallet.createAccount()
            val accounts = wallet.getAccounts(true)
            var accountsBalance = BigInteger.ZERO
            var accountsUnlockedBalance = BigInteger.ZERO
            for (account in accounts) {
                accountsBalance = accountsBalance.add(account.balance!!)
                accountsUnlockedBalance = accountsUnlockedBalance.add(account.unlockedBalance!!)

                var subaddressesBalance = BigInteger.ZERO
                var subaddressesUnlockedBalance = BigInteger.ZERO
                for (subaddress in account.subaddresses!!) {
                    subaddressesBalance = subaddressesBalance.add(subaddress.balance!!)
                    subaddressesUnlockedBalance = subaddressesUnlockedBalance.add(subaddress.unlockedBalance!!)

                    // consistent with the accounts
                    assertEquals(subaddress.balance, wallet.getBalance(subaddress.accountIndex, subaddress.index))
                    assertEquals(subaddress.unlockedBalance, wallet.getUnlockedBalance(subaddress.accountIndex, subaddress.index))
                }
                assertEquals(subaddressesBalance, wallet.getBalance(account.index))
                assertEquals(subaddressesUnlockedBalance, wallet.getUnlockedBalance(account.index))
            }
            assertEquals(accountsBalance, wallet.getBalance())
            assertEquals(accountsUnlockedBalance, wallet.getUnlockedBalance())
        }
    }

    // ------------------------------- ADDRESS BOOK ----------------------------------

    private fun assertEntry(entry: MoneroAddressBookEntry, index: Long, address: String, description: String) {
        assertEquals(index, entry.index)
        assertEquals(address, entry.address)
        assertEquals(description, entry.description)
        assertNull(entry.paymentId)
    }

    @Test
    open fun testAddressBookOfANewWalletIsEmpty() {
        withRandomWallet { wallet ->
            assertEquals(emptyList(), wallet.getAddressBookEntries())
            assertEquals(emptyList(), wallet.getAddressBookEntries(emptyList()))
        }
    }

    // Has an address book: adds standard addresses, gets them all or by index, edits them and deletes them
    @Test
    open fun testAddressBookAddsEditsAndDeletesEntries() {
        withRandomWallet { wallet ->
            val indices = (0 until 5).map { wallet.addAddressBookEntry(primaryAddress, "hi there!") }
            assertEquals(listOf(0L, 1L, 2L, 3L, 4L), indices)

            val entries = wallet.getAddressBookEntries()
            assertEquals(5, entries.size)
            entries.forEachIndexed { i, entry -> assertEntry(entry, indices[i], primaryAddress, "hi there!") }

            for (index in indices) wallet.editAddressBookEntry(index, null, "hello there!!")
            wallet.getAddressBookEntries(indices).forEachIndexed { i, entry -> assertEntry(entry, indices[i], primaryAddress, "hello there!!") }

            // each delete takes the entry at the index, and the entries after it move one place down
            repeat(5) { wallet.deleteAddressBookEntry(indices[0]) }
            assertEquals(emptyList(), wallet.getAddressBookEntries())
        }
    }

    @Test
    open fun testAddressBookGetsTheEntriesOfTheIndicesInTheOrderOfTheIndices() {
        withRandomWallet { wallet ->
            for (i in 0 until 4) wallet.addAddressBookEntry(primaryAddress, "entry $i")
            assertEquals(listOf("entry 3", "entry 1"), wallet.getAddressBookEntries(listOf(3L, 1L)).map { it.description })
            assertEquals(listOf("entry 2"), wallet.getAddressBookEntries(listOf(2L)).map { it.description })
        }
    }

    @Test
    open fun testAddressBookDeleteMovesTheEntriesAfterItOnePlaceDown() {
        withRandomWallet { wallet ->
            for (i in 0 until 4) wallet.addAddressBookEntry(primaryAddress, "entry $i")
            wallet.deleteAddressBookEntry(1)
            val entries = wallet.getAddressBookEntries()
            assertEquals(listOf(0L, 1L, 2L), entries.map { it.index })
            assertEquals(listOf("entry 0", "entry 2", "entry 3"), entries.map { it.description })
        }
    }

    // Adds subaddresses and integrated addresses, which come back as they were given and with no payment id
    @Test
    open fun testAddressBookKeepsSubaddressesAndIntegratedAddresses() {
        withRandomWallet { wallet ->
            val subaddress = wallet.createSubaddress(0).address!!
            val integrated = (0 until 5).map { wallet.getIntegratedAddress(null, "03284e41c342f03$it").integratedAddress!! }
            val subaddressIndex = wallet.addAddressBookEntry(subaddress, "a subaddress")
            val integratedIndices = integrated.mapIndexed { i, address -> wallet.addAddressBookEntry(address, "integrated $i") }

            assertEntry(wallet.getAddressBookEntries(listOf(subaddressIndex)).single(), subaddressIndex, subaddress, "a subaddress")
            wallet.getAddressBookEntries(integratedIndices).forEachIndexed { i, entry -> assertEntry(entry, integratedIndices[i], integrated[i], "integrated $i") }
            assertEquals(6, wallet.getAddressBookEntries().size)
        }
    }

    @Test
    open fun testAddressBookEditKeepsWhatIsNull() {
        withRandomWallet { wallet ->
            val other = wallet.getPrimaryAddress()
            val index = wallet.addAddressBookEntry(primaryAddress, "first")

            wallet.editAddressBookEntry(index, other, null)
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, other, "first")

            wallet.editAddressBookEntry(index, null, "second")
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, other, "second")

            wallet.editAddressBookEntry(index, primaryAddress, "third")
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, primaryAddress, "third")

            // an empty description takes the description away, and nothing to change changes nothing
            wallet.editAddressBookEntry(index, null, "")
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, primaryAddress, "")
            wallet.editAddressBookEntry(index, null, null)
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, primaryAddress, "")
        }
    }

    @Test
    open fun testAddressBookEntryWithNoDescriptionHasAnEmptyOne() {
        withRandomWallet { wallet ->
            val index = wallet.addAddressBookEntry(primaryAddress)
            assertEntry(wallet.getAddressBookEntries(listOf(index)).single(), index, primaryAddress, "")
        }
    }

    @Test
    open fun testAddressBookWithWhatIsNotValidIsAnError() {
        withRandomWallet { wallet ->
            assertFailsWith<MoneroError> { wallet.addAddressBookEntry("invalid_address", "no") }
            assertEquals(emptyList(), wallet.getAddressBookEntries())

            val index = wallet.addAddressBookEntry(primaryAddress, "yes")
            assertFailsWith<MoneroError> { wallet.editAddressBookEntry(index, "invalid_address", "no") }
            assertFailsWith<MoneroError> { wallet.editAddressBookEntry(index + 1, null, "no") }
            assertFailsWith<MoneroError> { wallet.deleteAddressBookEntry(index + 1) }
            assertFailsWith<MoneroError> { wallet.getAddressBookEntries(listOf(index + 1)) }
            assertFailsWith<MoneroError> { wallet.getAddressBookEntries(listOf(-1L)) }
            assertEntry(wallet.getAddressBookEntries().single(), index, primaryAddress, "yes")
        }
    }
}
