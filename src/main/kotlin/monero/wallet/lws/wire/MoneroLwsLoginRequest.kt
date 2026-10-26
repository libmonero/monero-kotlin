package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Models the body of POST /login: the credentials of the account, and whether the login asks the server to create the
 * account when it has none for the address.
 */
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
