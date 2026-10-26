package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.math.BigInteger

/**
 * Models the response of /get_address_info. The server writes lookahead and lookahead_fail only when the scan window
 * moved or failed, leaving them out at the defaults, and it leaves spent_outputs and rates out entirely when the
 * address has no candidate spends and the daemon provides no exchange rates (light_wallet.cpp:479-491).
 */
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

    // the exchange rates of Monero against other currencies, which the server writes only when the daemon provides
    // them (light_wallet.cpp:487)
    var rates: MoneroLwsRates? = null

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
        this.rates = other.rates?.copy()
    }

    fun copy(): MoneroLwsAddressInfoResponse {
        return MoneroLwsAddressInfoResponse(this)
    }
}
