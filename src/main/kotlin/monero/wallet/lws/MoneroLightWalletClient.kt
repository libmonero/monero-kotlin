package monero.wallet.lws

import monero.wallet.lws.model.MoneroLwsAddressInfo
import monero.wallet.lws.model.MoneroLwsTransaction
import monero.wallet.lws.wire.MoneroLwsLoginResponse

/**
 * A client of a Monero light wallet server (lws): a wallet that scans
 * through it needs no monerod and no wallet-rpc of its own. The calls
 * are suspend bc an lws server is plain http, so an implementation
 * needs no native library. Nothing implements this yet, monero-c has no
 * light wallet functions.
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
     * Get the transactions of the address since the last fetch.
     */
    suspend fun getAddressTxs(): List<MoneroLwsTransaction>

    /**
     * Send a signed transaction to the network through the server.
     *
     * @param signedTxHex is the signed transaction in hexadecimal, in the p2p format of monerod
     * @return true if the server took the transaction
     */
    suspend fun submitRawTx(signedTxHex: String): Boolean

    override fun close()
}
