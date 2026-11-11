package monero.wallet

import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import monero.NotImplemented
import monero.NotSupported
import monero.RegtestSupport
import monero.ModelTestSupport.assertSameJson
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.common.MoneroUtils
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroAccountTag
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.Tag

/**
 * The wallet of a monero-wallet-rpc server, against the server of src/test/docker, like the tests of monero-python
 * (tests/test_monero_wallet_rpc.py), which extend the common tests: the ones of [BaseMoneroWalletTest], that every wallet has to pass,
 * and the ones that create wallets on the server, open them and close them. They need monero-c and the server.
 */
@Tag("integration")
class MoneroWalletRpcIntegrationTest : BaseMoneroWalletTest() {

    private val walletPassword = "supersecretpassword123"

    // the server of the wallets of these tests, which are on the same one so that a test opens by name a wallet that it made: the first
    // server holds the wallet that the other tests share
    private val server = 1

    @BeforeTest
    fun requireLibraryAndServer() {
        RegtestSupport.assumeWalletRpcAvailable(server)
    }

    // ------------------------------- THE COMMON TESTS ----------------------------------

    // The test wallet of the common tests is the wallet of the test seed, made on the server. The test wallet of monero-python is one that
    // has been used: it has several accounts, and several subaddresses in the first one, which a wallet that a server makes has not
    override fun createTestWallet(): MoneroWallet {
        val wallet = createWalletOnServer(config().also {
            it.seed = seed
            it.restoreHeight = 0
        })
        try {
            while (wallet.getAccounts().size < 4) wallet.createAccount()
            while (wallet.getSubaddresses(0).size < 3) wallet.createSubaddress(0)
            return wallet
        } catch (e: Throwable) {
            runCatching { wallet.close() }
            throw e
        }
    }

    override fun createRandomWallet(): MoneroWallet = createWalletOnServer(config())

    override fun closeWallet(wallet: MoneroWallet) = wallet.close(false)

    override fun seedLanguages(wallet: MoneroWallet): List<String> = (wallet as MoneroWalletRpc).getSeedLanguages()

    override val defaultLanguage: String = "English"

    override fun subaddressOf(wallet: MoneroWallet): String = wallet.createSubaddress(0).address!!

    override val makesAPaymentId: Boolean = true

    private fun createWalletOnServer(config: MoneroWalletConfig): MoneroWalletRpc {
        val wallet = newClient()
        try {
            wallet.createWallet(config)
            return wallet
        } catch (e: Throwable) {
            runCatching { wallet.close() }
            throw e
        }
    }

    // monero-wallet-rpc does not say the language of the seed of a wallet
    @Test
    @NotSupported
    override fun testGetSeedLanguage() = super.testGetSeedLanguage()

    // monero-wallet-rpc does not give the public keys of a wallet, which monero-python marks as a TODO of monero-project
    @Test
    @NotImplemented
    override fun testGetPublicViewKey() = super.testGetPublicViewKey()

    @Test
    @NotImplemented
    override fun testGetPublicSpendKey() = super.testGetPublicSpendKey()

    // Supports the multisig sample code: wallets of two servers make a multisig wallet of 1 of 2 and of 2 of 2, each of which has the address
    // of the setup and says that it is multisig and ready, with its threshold and its participants
    @Test
    fun testMultisigSample() {
        RpcTestWallets.assumeAvailable()
        for (m in 1..2) {
            MultisigSupport.setup(m, 2, { RpcTestWallets.create() }, FullTestWallets.PASSWORD).use { setup ->
                MoneroUtils.validateAddress(setup.address, MoneroNetworkType.MAINNET)
                for (wallet in setup.participants) {
                    assertEquals(setup.address, wallet.getPrimaryAddress())
                    val info = wallet.getMultisigInfo()
                    assertEquals(true, info.isMultisig)
                    assertEquals(true, info.isReady)
                    assertEquals(m, info.threshold)
                    assertEquals(2, info.numParticipants)
                }
            }
        }
    }

    // Does not report the progress of a sync, which the server does not tell
    @Test
    fun testSyncProgress() {
        withTestWallet { wallet ->
            val listener = WalletNotificationCollector()
            val expected = "Monero Wallet RPC does not support reporting sync progress"
            assertEquals(expected, assertFailsWith<MoneroError> { wallet.sync(listener) }.message)
            assertEquals(expected, assertFailsWith<MoneroError> { wallet.sync(0, listener) }.message)
        }
    }

    // Can get a subaddress, with the function of the server that gets the subaddresses of an account
    @Test
    fun testGetSubaddressRpc() {
        withTestWallet { wallet ->
            val accountIdx = 0
            val subaddressIdx = 0
            val subaddress = wallet.getSubaddress(accountIdx, subaddressIdx)
            validateSubaddress(subaddress)
            assertEquals(accountIdx, subaddress.accountIndex)
            assertEquals(subaddressIdx, subaddress.index)

            val plural = wallet.getSubaddresses(accountIdx, listOf(subaddressIdx))
            assertEquals(1, plural.size)
            assertSameJson(MoneroJson.serialize(subaddress), MoneroJson.serialize(plural[0]))
            assertEquals(subaddress.address, wallet.getAddress(accountIdx, subaddressIdx))
        }
    }

    // The address of a subaddress that the wallet has not made is empty, and the server has no account that the wallet has not made
    @Test
    fun testGetSubaddressAddressOutOfRange() {
        withTestWallet { wallet ->
            val accounts = wallet.getAccounts(true)
            val accountIdx = accounts.size - 1
            val subaddressIdx = accounts[accountIdx].subaddresses!!.size
            assertEquals("", wallet.getAddress(accountIdx, subaddressIdx))
            assertFailsWith<MoneroError> { wallet.getAddress(accounts.size, 0) }
        }
    }

    // Can save the wallet
    @Test
    fun testSave() {
        withTestWallet { wallet -> wallet.save() }
    }

    // The server does not tell the height of the chain, its peers, or the height of a date, and the wallet does not tell its network, where it
    // restores from, or whether it and its daemon are synced
    @Test
    @NotSupported
    fun testGetDaemonMaxPeerHeight() {
        withTestWallet { wallet -> wallet.getDaemonMaxPeerHeight() }
    }

    @Test
    @NotSupported
    fun testGetDaemonHeight() {
        withTestWallet { wallet -> wallet.getDaemonHeight() }
    }

    @Test
    @NotSupported
    fun testGetHeightByDate() {
        withTestWallet { wallet -> wallet.getHeightByDate(2025, 1, 1) }
    }

    @Test
    @NotSupported
    fun testGetNetworkType() {
        withTestWallet { wallet -> wallet.getNetworkType() }
    }

    @Test
    @NotSupported
    fun testGetRestoreHeight() {
        withTestWallet { wallet -> wallet.getRestoreHeight() }
    }

    @Test
    @NotSupported
    fun testIsSynced() {
        withTestWallet { wallet -> wallet.isSynced() }
    }

    @Test
    @NotSupported
    fun testIsDaemonSynced() {
        withTestWallet { wallet -> wallet.isDaemonSynced() }
    }

    @Test
    @NotSupported
    fun testIsDaemonTrusted() {
        withTestWallet { wallet -> wallet.isDaemonTrusted() }
    }

    // ------------------------------- THE TESTS OF THE SERVER ----------------------------------

    private fun newClient() = MoneroWalletRpc(RegtestSupport.walletRpcConnection(server))

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
    fun testCreateWalletRandomRpc() {
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
    fun testCreateWalletFromSeedRpc() {
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
    fun testGetSeedLanguagesWithoutWallet() {
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
            assertEquals(RegtestSupport.walletRpcServerUri(server), connection.uri)
            assertEquals(RegtestSupport.walletRpcUsername, connection.username)
        }
    }

    // a wallet of the server with four accounts, and a subaddress in the second one
    private fun walletWithAccounts(): MoneroWalletRpc {
        val wallet = newClient()
        try {
            wallet.createWallet(config())
            repeat(3) { wallet.createAccount("account ${it + 1}") }
            wallet.createSubaddress(1, "subaddress")
            return wallet
        } catch (e: Throwable) {
            runCatching { wallet.close() }
            throw e
        }
    }

    // Can get the accounts of the wallet, with their subaddresses when they are asked for
    @Test
    fun testGetAccounts() {
        walletWithAccounts().use { wallet ->
            val accounts = wallet.getAccounts()
            assertEquals(listOf(0, 1, 2, 3), accounts.map { it.index })
            assertTrue(accounts.all { it.primaryAddress != null && it.balance != null && it.unlockedBalance != null })

            // the label of an account is the one of its first subaddress
            val withSubaddresses = wallet.getAccounts(true)
            assertEquals(listOf("account 1", "account 2", "account 3"), withSubaddresses.drop(1).map { it.subaddresses!![0].label })
            assertEquals(listOf(1, 2, 1, 1), withSubaddresses.map { it.subaddresses!!.size })
            assertEquals(wallet.getPrimaryAddress(), withSubaddresses[0].subaddresses!![0].address)
        }
    }

    // Can tag accounts and query accounts by tag
    @Test
    fun testAccountTags() {
        walletWithAccounts().use { wallet ->
            // tag some of the accounts
            val tag = MoneroAccountTag("my_tag_" + UUID.randomUUID(), "my tag label", listOf(0, 1))
            wallet.tagAccounts(tag.tag!!, tag.accountIndices!!)

            // query accounts by tag
            val tagged = wallet.getAccounts(false, tag.tag)
            assertEquals(listOf(0, 1), tagged.map { it.index })
            assertEquals(listOf(tag.tag, tag.tag), tagged.map { it.tag })

            // set tag label
            wallet.setAccountTagLabel(tag.tag!!, tag.label!!)

            // fetch tags and ensure the new tag is contained
            val found = wallet.getAccountTags().single { it.tag == tag.tag }
            assertEquals(tag.label, found.label)
            assertEquals(tag.accountIndices, found.accountIndices)

            // re-tag an account
            val tag2 = MoneroAccountTag("my_tag_" + UUID.randomUUID(), "my tag label 2", listOf(1))
            wallet.tagAccounts(tag2.tag!!, tag2.accountIndices!!)
            val tagged2 = wallet.getAccounts(false, tag2.tag)
            assertEquals(listOf(1), tagged2.map { it.index })
            assertEquals(tag2.tag, tagged2[0].tag)

            // re-query the original tag, which applies to one account now
            val retagged = wallet.getAccounts(false, tag.tag)
            assertEquals(listOf(0), retagged.map { it.index })
            assertEquals(tag.tag, retagged[0].tag)

            // untag and query accounts
            wallet.untagAccounts(listOf(0, 1))
            assertEquals(emptyList(), wallet.getAccountTags())
            assertFailsWith<MoneroError> { wallet.getAccounts(false, tag.tag) }

            // a tag that was never registered may be an error or nothing
            runCatching { assertEquals(emptyList(), wallet.getAccounts(false, "non_existing_tag")) }
        }
    }

    // Can not tag an account that is not there
    @Test
    fun testTagAccountThatIsNotThere() {
        walletWithAccounts().use { wallet ->
            assertFailsWith<MoneroError> { wallet.tagAccounts("my_tag", listOf(99)) }
            assertEquals(emptyList(), wallet.getAccountTags())
        }
    }
}
