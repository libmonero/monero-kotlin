package monero.wallet.lws.wire

/**
 * Models the body of POST /submit_raw_tx.
 */
class MoneroLwsSubmitRawTxRequest {
    // the signed transaction in hexadecimal, in the p2p format of monerod
    var tx: String? = null

    constructor()

    constructor(other: MoneroLwsSubmitRawTxRequest) {
        this.tx = other.tx
    }

    fun copy(): MoneroLwsSubmitRawTxRequest {
        return MoneroLwsSubmitRawTxRequest(this)
    }
}
