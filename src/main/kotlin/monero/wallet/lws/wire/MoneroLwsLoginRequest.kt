package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty

// body of POST /login
class MoneroLwsLoginRequest {
    var address: String? = null
    @JsonProperty("view_key") var viewKey: String? = null
    @JsonProperty("create_account") var createAccount: Boolean? = null
    @JsonProperty("generated_locally") var generatedLocally: Boolean? = null

    constructor()

    constructor(other: MoneroLwsLoginRequest) {
        this.address = other.address
        this.viewKey = other.viewKey
        this.createAccount = other.createAccount
        this.generatedLocally = other.generatedLocally
    }

    fun copy(): MoneroLwsLoginRequest {
        return MoneroLwsLoginRequest(this)
    }
}
