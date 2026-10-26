package monero.wallet.lws

import monero.wallet.lws.model.MoneroLwsAddressInfo
import monero.wallet.lws.model.MoneroLwsTransaction
import monero.wallet.lws.wire.MoneroLwsLoginResponse
import monero.wallet.lws.wire.MoneroLwsVersion

/**
 * A client of a Monero light wallet server: a wallet that scans through it needs no monerod and no wallet RPC of its
 * own. The calls are suspend because a light wallet server is plain http, so an implementation of them needs no
 * native library.
 */
interface MoneroLightWalletClient : AutoCloseable {

    /**
     * Log the address in at the server and get its scan state back.
     */
    suspend fun login(): MoneroLwsLoginResponse

    /**
     * Get the balances of the address and the heights of its scan.
     */
    suspend fun getAddressInfo(): MoneroLwsAddressInfo

    /**
     * Get the transactions of the address as its scan has them now. The server sends the whole history of the account
     * on every call, so a wallet that wants the transactions it has not seen diffs the responses itself.
     */
    suspend fun getAddressTxs(): List<MoneroLwsTransaction>

    /**
     * Send a signed transaction to the network through the server.
     *
     * @param signedTxHex is the signed transaction in hexadecimal, in the p2p format of monerod
     * @return true if the server took the transaction
     */
    suspend fun submitRawTx(signedTxHex: String): Boolean

    /**
     * Get the version of the server, and of the monerod behind it.
     *
     * @return the version of the server
     */
    suspend fun getVersion(): MoneroLwsVersion

    override fun close()
}
