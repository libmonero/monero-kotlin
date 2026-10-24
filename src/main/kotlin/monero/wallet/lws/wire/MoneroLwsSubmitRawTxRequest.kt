package monero.wallet.lws.wire

// body of POST /submit_raw_tx
class MoneroLwsSubmitRawTxRequest {
    // signed tx hex, monerod p2p format
    var tx: String? = null

    constructor()

    constructor(other: MoneroLwsSubmitRawTxRequest) {
        this.tx = other.tx
    }

    fun copy(): MoneroLwsSubmitRawTxRequest {
        return MoneroLwsSubmitRawTxRequest(this)
    }
}
