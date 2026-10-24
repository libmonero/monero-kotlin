package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.math.BigInteger

// an output of the address that a transaction spent
class MoneroLwsSpend {
    @JsonDeserialize(using = MoneroLwsAmountDeserializer::class)
    @JsonSerialize(using = MoneroLwsAmountSerializer::class)
    var amount: BigInteger? = null

    @JsonProperty("key_image") var keyImage: String? = null

    // the server always writes sender (light_wallet.cpp:470) but no
    // capture has a spend in it, so keep it forgiving
    var sender: MoneroLwsSubaddressIndex? = null

    constructor()

    constructor(other: MoneroLwsSpend) {
        this.amount = other.amount
        this.keyImage = other.keyImage
        this.sender = other.sender?.copy()
    }

    fun copy(): MoneroLwsSpend {
        return MoneroLwsSpend(this)
    }
}
