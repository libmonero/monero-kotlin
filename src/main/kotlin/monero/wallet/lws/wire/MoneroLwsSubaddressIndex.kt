package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Models a subaddress index on the wire: maj_i is the account, and min_i the subaddress within it. The same shape
 * shows up as lookahead, recipient and sender, which are all just index pairs.
 */
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
