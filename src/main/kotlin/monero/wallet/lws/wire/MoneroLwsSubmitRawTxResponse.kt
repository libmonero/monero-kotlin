package monero.wallet.lws.wire

/**
 * Models the response of /submit_raw_tx: the status is "OK" when the server took the transaction, whether it relayed
 * it or not, and "Failed" when the daemon behind the server rejected it (rest_server.cpp:1630-1636).
 */
class MoneroLwsSubmitRawTxResponse {
    var status: String? = null

    constructor()

    constructor(other: MoneroLwsSubmitRawTxResponse) {
        this.status = other.status
    }

    fun copy(): MoneroLwsSubmitRawTxResponse {
        return MoneroLwsSubmitRawTxResponse(this)
    }
}
