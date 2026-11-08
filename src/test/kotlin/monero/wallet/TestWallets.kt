package monero.wallet

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import monero.RegtestSupport
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroWalletConfig

/**
 * The wallets that the integration tests of the functions that every wallet has use, of one kind of wallet: the tests are written once
 * and run for the full wallet and for the wallet of a monero-wallet-rpc server, like the tests of monero-python that its wallet types
 * share (tests/test_monero_wallet_common.py). A kind says how to make a wallet that is connected to the regtest node of src/test/docker.
 */
interface TestWallets {

    /**
     * Skip the test if monero-c can not be loaded, or what the wallets need is not running. The node is started and its chain is mined
     * if it has not been.
     */
    fun assumeAvailable()

    /**
     * Make a wallet that is connected to the node, and has not synced. A wallet with no seed is a random one, which starts at the height
     * of the chain. The test closes it.
     *
     * @param seed is the seed to restore the wallet from, or null for a random wallet
     * @param restoreHeight is the height to restore it from, or null for the default
     */
    fun create(seed: String? = null, restoreHeight: Long? = null): MoneroWalletNative

    /**
     * Make a view-only wallet of an address, which has never refreshed from the node, so it can import outputs. The test closes it.
     *
     * @param address is the primary address of the wallet
     * @param privateViewKey is the private view key of the wallet
     */
    fun createViewOnly(address: String, privateViewKey: String): MoneroWalletNative
}

/**
 * Full wallets, which are kept in a temporary directory that is removed when the JVM ends.
 */
object FullTestWallets : TestWallets {

    private val directory: Path by lazy {
        Files.createTempDirectory("full-wallets-").also { dir -> Runtime.getRuntime().addShutdownHook(Thread { dir.toFile().deleteRecursively() }) }
    }

    override fun assumeAvailable() {
        RegtestSupport.assumeAvailable()
    }

    override fun create(seed: String?, restoreHeight: Long?): MoneroWalletNative {
        val config = MoneroWalletConfig()
        config.path = directory.resolve("wallet-${System.nanoTime()}").toString()
        config.password = PASSWORD
        config.networkType = MoneroNetworkType.MAINNET
        config.server = RegtestSupport.daemonConnection()
        config.regtest = true
        config.seed = seed
        config.restoreHeight = restoreHeight
        return MoneroWalletFull.createWallet(config)
    }

    // it has no server, so it is offline
    override fun createViewOnly(address: String, privateViewKey: String): MoneroWalletNative {
        val config = MoneroWalletConfig()
        config.path = directory.resolve("wallet-${System.nanoTime()}").toString()
        config.password = PASSWORD
        config.networkType = MoneroNetworkType.MAINNET
        config.regtest = true
        config.primaryAddress = address
        config.privateViewKey = privateViewKey
        config.restoreHeight = 0
        return MoneroWalletFull.createWallet(config)
    }

    const val PASSWORD = "supersecretpassword123"
}

/**
 * Wallets of the monero-wallet-rpc server of src/test/docker, which the server keeps under a name that it has not another wallet with.
 */
object RpcTestWallets : TestWallets {

    override fun assumeAvailable() {
        RegtestSupport.assumeAvailable()
        RegtestSupport.assumeWalletRpcAvailable()
    }

    override fun create(seed: String?, restoreHeight: Long?): MoneroWalletNative {
        val config = MoneroWalletConfig()
        config.seed = seed
        config.restoreHeight = restoreHeight
        return create(config)
    }

    // the server refreshes its open wallet by itself every 20 seconds, so this one can import outputs until then: a test has to use it at once
    override fun createViewOnly(address: String, privateViewKey: String): MoneroWalletNative {
        val config = MoneroWalletConfig()
        config.primaryAddress = address
        config.privateViewKey = privateViewKey
        config.restoreHeight = 0
        return create(config)
    }

    private fun create(config: MoneroWalletConfig): MoneroWalletNative {
        val wallet = MoneroWalletRpc(RegtestSupport.walletRpcConnection())
        try {
            config.path = "kt-" + UUID.randomUUID().toString().replace("-", "")
            config.password = FullTestWallets.PASSWORD
            wallet.createWallet(config)
            return wallet
        } catch (e: Throwable) {
            runCatching { wallet.close() }
            throw e
        }
    }
}
