package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.math.BigInteger

// response of /get_address_info. lookahead and lookahead_fail only show
// up when the scan window moved or failed, the server leaves them out at
// defaults, and spent_outputs is omitted entirely when there are none
class MoneroLwsAddressInfoResponse {
    @JsonProperty("locked_funds")
    @JsonDeserialize(using = MoneroLwsAmountDeserializer::class)
    @JsonSerialize(using = MoneroLwsAmountSerializer::class)
    var lockedFunds: BigInteger? = null

    @JsonProperty("total_received")
    @JsonDeserialize(using = MoneroLwsAmountDeserializer::class)
    @JsonSerialize(using = MoneroLwsAmountSerializer::class)
    var totalReceived: BigInteger? = null

    @JsonProperty("total_sent")
    @JsonDeserialize(using = MoneroLwsAmountDeserializer::class)
    @JsonSerialize(using = MoneroLwsAmountSerializer::class)
    var totalSent: BigInteger? = null

    @JsonProperty("scanned_height") var scannedHeight: Long? = null
    @JsonProperty("scanned_block_height") var scannedBlockHeight: Long? = null
    @JsonProperty("start_height") var startHeight: Long? = null
    @JsonProperty("transaction_height") var transactionHeight: Long? = null
    @JsonProperty("blockchain_height") var blockchainHeight: Long? = null
    var lookahead: MoneroLwsSubaddressIndex? = null
    @JsonProperty("lookahead_fail") var lookaheadFail: Long? = null
    @JsonProperty("spent_outputs") var spentOutputs: List<MoneroLwsSpend>? = null

    constructor()

    constructor(other: MoneroLwsAddressInfoResponse) {
        this.lockedFunds = other.lockedFunds
        this.totalReceived = other.totalReceived
        this.totalSent = other.totalSent
        this.scannedHeight = other.scannedHeight
        this.scannedBlockHeight = other.scannedBlockHeight
        this.startHeight = other.startHeight
        this.transactionHeight = other.transactionHeight
        this.blockchainHeight = other.blockchainHeight
        this.lookahead = other.lookahead?.copy()
        this.lookaheadFail = other.lookaheadFail
        this.spentOutputs = other.spentOutputs?.map { it.copy() }
    }

    fun copy(): MoneroLwsAddressInfoResponse {
        return MoneroLwsAddressInfoResponse(this)
    }
}
