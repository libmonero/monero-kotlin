package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.math.BigInteger

/**
 * Models the response of /get_address_txs. The server leaves transactions out entirely when the address has none. A
 * scan loop fetches from start_height and continues at transaction_height + 1.
 */
class MoneroLwsAddressTxsResponse {
    @JsonProperty("total_received")
    @JsonDeserialize(using = MoneroLwsAmountDeserializer::class)
    @JsonSerialize(using = MoneroLwsAmountSerializer::class)
    var totalReceived: BigInteger? = null

    @JsonProperty("scanned_height") var scannedHeight: Long? = null
    @JsonProperty("scanned_block_height") var scannedBlockHeight: Long? = null
    @JsonProperty("start_height") var startHeight: Long? = null
    @JsonProperty("transaction_height") var transactionHeight: Long? = null
    @JsonProperty("blockchain_height") var blockchainHeight: Long? = null
    var lookahead: MoneroLwsSubaddressIndex? = null
    @JsonProperty("lookahead_fail") var lookaheadFail: Long? = null
    var transactions: List<MoneroLwsTx>? = null

    constructor()

    constructor(other: MoneroLwsAddressTxsResponse) {
        this.totalReceived = other.totalReceived
        this.scannedHeight = other.scannedHeight
        this.scannedBlockHeight = other.scannedBlockHeight
        this.startHeight = other.startHeight
        this.transactionHeight = other.transactionHeight
        this.blockchainHeight = other.blockchainHeight
        this.lookahead = other.lookahead?.copy()
        this.lookaheadFail = other.lookaheadFail
        this.transactions = other.transactions?.map { it.copy() }
    }

    fun copy(): MoneroLwsAddressTxsResponse {
        return MoneroLwsAddressTxsResponse(this)
    }
}
