package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty

// a subaddress index on the lws wire: maj_i is the account, min_i the
// subaddress in it. the same shape shows up as lookahead, recipient and
// sender, its just an index pair
class MoneroLwsSubaddressIndex {
    @JsonProperty("maj_i") var majorIndex: Long? = null
    @JsonProperty("min_i") var minorIndex: Long? = null

    constructor()

    constructor(other: MoneroLwsSubaddressIndex) {
        this.majorIndex = other.majorIndex
        this.minorIndex = other.minorIndex
    }

    fun copy(): MoneroLwsSubaddressIndex {
        return MoneroLwsSubaddressIndex(this)
    }
}
