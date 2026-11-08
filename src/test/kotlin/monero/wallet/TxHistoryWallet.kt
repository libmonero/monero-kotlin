package monero.wallet

import java.math.BigInteger
import monero.RegtestSupport
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroTxConfig

/**
 * The wallet that the tests of the transactions and of the transfers read, against a regtest node. It has what those tests of
 * monero-python need and a fresh regtest wallet has not: a second account with subaddresses that received funds from the first,
 * transactions between the two accounts, transactions to an address that is not its own, and a transaction that is in the pool. It is
 * made once for all the tests of a kind of wallet ([TestWallets]), which only read it, and closed when the JVM ends.
 */
object TxHistoryWallet {

    // the payment id of the transaction that the wallet sends to an integrated address of its own
    const val PAYMENT_ID = "54491f3bb3572a37"

    private val wallets = HashMap<TestWallets, MoneroWalletNative>()

    /**
     * The wallet of a kind, which is made on the first call. It needs monero-c and the regtest node of src/test/docker, and the server
     * for the wallet of a server.
     */
    @Synchronized
    fun get(kind: TestWallets): MoneroWalletNative = wallets[kind] ?: build(kind).also {
        wallets[kind] = it
        Runtime.getRuntime().addShutdownHook(Thread { close(kind) })
    }

    /**
     * The full wallet.
     */
    fun get(): MoneroWalletFull = get(FullTestWallets) as MoneroWalletFull

    @Synchronized
    private fun close(kind: TestWallets) {
        wallets.remove(kind)?.let { runCatching { it.close() } }
    }

    // Five coinbases that matured go to the first account, which pays the second one with one transaction to three of its subaddresses, then
    // pays one of its own subaddresses, then pays its integrated address with a payment id, then pays an address that is not its own twice, then the second account pays the first one, and the last payment is left in the pool.
    private fun build(kind: TestWallets): MoneroWalletNative {
        val wallet = kind.createShared()
        try {
            RegtestSupport.mineBlocks(5, wallet.getPrimaryAddress())
            RegtestSupport.mineBlocks(130)
            wallet.sync()
            wallet.createAccount()
            wallet.createSubaddress(1)
            wallet.createSubaddress(1)
            wallet.createSubaddress(0)

            send(wallet, 0, listOf(wallet.getAddress(1, 0) to 1, wallet.getAddress(1, 1) to 2, wallet.getAddress(1, 2) to 3))
            confirm(wallet)
            send(wallet, 0, listOf(wallet.getAddress(0, 1) to 1))
            confirm(wallet)
            send(wallet, 0, listOf(wallet.getIntegratedAddress(null, PAYMENT_ID).integratedAddress!! to 1))
            confirm(wallet)
            send(wallet, 0, listOf(RegtestSupport.MINING_ADDRESS to 1))
            confirm(wallet)
            send(wallet, 0, listOf(RegtestSupport.MINING_ADDRESS to 2))
            confirm(wallet)
            send(wallet, 1, listOf(wallet.getAddress(0, 0) to 1))
            confirm(wallet)
            send(wallet, 0, listOf(wallet.getAddress(1, 2) to 1))
            wallet.sync()
            return wallet
        } catch (e: Throwable) {
            runCatching { wallet.close() }
            throw e
        }
    }

    private fun send(wallet: MoneroWalletNative, accountIdx: Int, payments: List<Pair<String, Int>>) {
        val config = MoneroTxConfig()
        config.accountIndex = accountIdx
        config.destinations = payments.map { (address, xmr) -> MoneroDestination(address, BigInteger.valueOf(xmr * 1_000_000_000_000L)) }
        config.relay = true
        wallet.createTxs(config)
    }

    private fun confirm(wallet: MoneroWalletNative) {
        RegtestSupport.mineBlocks(12)
        wallet.sync()
    }
}
