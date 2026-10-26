package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.math.BigInteger

/**
 * Models a transaction on the wire, kept separate from the MoneroLwsTransaction that the client computes from it.
 * The actual amounts come from total_received, total_sent and spent_outputs, not from a guess with the view key. The
 * server always writes fee and recipient (light_wallet.cpp:521,528), so the real debit of an outgoing transaction is
 * its total_sent plus its fee, and a transaction in the mempool has no height and no timestamp.
 */
class MoneroLwsTx {
    var id: Long? = null
    var hash: String? = null
    var timestamp: String? = null

    @JsonProperty("total_received")
    @JsonDeserialize(using = MoneroLwsAmountDeserializer::class)
    @JsonSerialize(using = MoneroLwsAmountSerializer::class)
    var totalReceived: BigInteger? = null

    @JsonProperty("total_sent")
    @JsonDeserialize(using = MoneroLwsAmountDeserializer::class)
    @JsonSerialize(using = MoneroLwsAmountSerializer::class)
    var totalSent: BigInteger? = null

    @JsonDeserialize(using = MoneroLwsAmountDeserializer::class)
    @JsonSerialize(using = MoneroLwsAmountSerializer::class)
    var fee: BigInteger? = null

    @JsonProperty("unlock_time") var unlockTime: BigInteger? = null
    var height: Long? = null
    @JsonProperty("coinbase") var isCoinbase: Boolean? = null
    @JsonProperty("mempool") var isMempool: Boolean? = null
    var mixin: Long? = null
    var recipient: MoneroLwsSubaddressIndex? = null
    @JsonProperty("spent_outputs") var spentOutputs: List<MoneroLwsSpend>? = null

    constructor()

    constructor(other: MoneroLwsTx) {
        this.id = other.id
        this.hash = other.hash
        this.timestamp = other.timestamp
        this.totalReceived = other.totalReceived
        this.totalSent = other.totalSent
        this.fee = other.fee
        this.unlockTime = other.unlockTime
        this.height = other.height
        this.isCoinbase = other.isCoinbase
        this.isMempool = other.isMempool
        this.mixin = other.mixin
        this.recipient = other.recipient?.copy()
        this.spentOutputs = other.spentOutputs?.map { it.copy() }
    }

    fun copy(): MoneroLwsTx {
        return MoneroLwsTx(this)
    }
}
