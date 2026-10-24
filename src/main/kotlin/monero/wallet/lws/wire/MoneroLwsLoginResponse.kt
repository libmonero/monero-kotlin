package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty

// response of /login, straight from live captures: new_address,
// generated_locally, lookahead and nothing else. the spec still claims
// start_height, its stale, light_wallet.cpp never sends it. the scan
// start comes from get_address_info
class MoneroLwsLoginResponse {
    @JsonProperty("new_address") var isNewAddress: Boolean? = null
    @JsonProperty("generated_locally") var generatedLocally: Boolean? = null
    var lookahead: MoneroLwsSubaddressIndex? = null

    constructor()

    constructor(other: MoneroLwsLoginResponse) {
        this.isNewAddress = other.isNewAddress
        this.generatedLocally = other.generatedLocally
        this.lookahead = other.lookahead?.copy()
    }

    fun copy(): MoneroLwsLoginResponse {
        return MoneroLwsLoginResponse(this)
    }
}
