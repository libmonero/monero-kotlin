package monero.wallet.lws.wire

// response of /submit_raw_tx, "success" when the server took the tx
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
