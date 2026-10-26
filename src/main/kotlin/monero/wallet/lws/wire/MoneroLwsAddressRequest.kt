package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Models the body of POST /get_address_info and POST /get_address_txs, which take the same address and view key.
 */
class MoneroLwsAddressRequest {
    var address: String? = null
    @JsonProperty("view_key") var viewKey: String? = null

    constructor()

    constructor(other: MoneroLwsAddressRequest) {
        this.address = other.address
        this.viewKey = other.viewKey
    }

    fun copy(): MoneroLwsAddressRequest {
        return MoneroLwsAddressRequest(this)
    }
}
