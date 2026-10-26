package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.math.BigInteger

/**
 * Models an output of the address that a transaction spent. The server writes all six fields of a spend, which
 * light_wallet.cpp:462-472 shows, and no capture of the test account has a spend in it to pin them.
 */
class MoneroLwsSpend {
    @JsonDeserialize(using = MoneroLwsAmountDeserializer::class)
    @JsonSerialize(using = MoneroLwsAmountSerializer::class)
    var amount: BigInteger? = null

    @JsonProperty("key_image") var keyImage: String? = null

    @JsonProperty("tx_pub_key") var txPubKey: String? = null

    @JsonProperty("out_index") var outIndex: Long? = null

    var mixin: Long? = null

    var sender: MoneroLwsSubaddressIndex? = null

    constructor()

    constructor(other: MoneroLwsSpend) {
        this.amount = other.amount
        this.keyImage = other.keyImage
        this.txPubKey = other.txPubKey
        this.outIndex = other.outIndex
        this.mixin = other.mixin
        this.sender = other.sender?.copy()
    }

    fun copy(): MoneroLwsSpend {
        return MoneroLwsSpend(this)
    }
}
