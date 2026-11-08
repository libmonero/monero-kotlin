package monero.wallet.model

import monero.common.MoneroError

/**
 * Configures a query for the transfers of a wallet: all of them are returned, except the ones that do not meet the
 * criteria that are set. The query of the transaction of the transfers is in txQuery.
 */
class MoneroTransferQuery : MoneroTransfer {

    // true to match the incoming transfers, false to match the outgoing one, null to match both. A transfer of a
    // transaction whose direction is not known yet meets neither: monero-java has it meet both
    override var isIncoming: Boolean? = null
    var address: String? = null
    var addresses: List<String>? = null
    var subaddressIndex: Int? = null
    var subaddressIndices: List<Int>? = null
    var destinations: List<MoneroDestination>? = null
    var hasDestinations: Boolean? = null
    var txQuery: MoneroTxQuery? = null

    constructor() : super()

    constructor(query: MoneroTransferQuery) : super(query) {
        this.isIncoming = query.isIncoming
        this.address = query.address
        this.addresses = query.addresses?.let { ArrayList(it) }
        this.subaddressIndex = query.subaddressIndex
        this.subaddressIndices = query.subaddressIndices?.let { ArrayList(it) }
        this.destinations = query.destinations?.map { it.copy() }
        this.hasDestinations = query.hasDestinations
        this.txQuery = query.txQuery?.copy()
    }

    override fun copy(): MoneroTransferQuery {
        return MoneroTransferQuery(this)
    }

    /**
     * Indicates if a transfer meets the criteria of this query: every field of the query that is set has to be the same in the
     * transfer. The address and the subaddress index of an incoming transfer have to be the ones of the query, or one of the ones in its
     * lists; for an outgoing transfer they have to be among its addresses and subaddress indices, or to intersect with the lists. A query
     * that asks whether there are destinations never meets an incoming transfer.
     *
     * @param transfer is the transfer to check. It has to be an incoming or an outgoing transfer
     * @param queryParent indicates if the query of the transaction of the transfer is checked too
     * @return true if the transfer meets the criteria
     * @throws MoneroError if the transfer is neither an incoming nor an outgoing transfer
     */
    fun meetsCriteria(transfer: MoneroTransfer, queryParent: Boolean = true): Boolean {

        // filter on the fields that every transfer has
        if (isIncoming != null && isIncoming != transfer.isIncoming) return false
        if (amount != null && amount != transfer.amount) return false
        if (accountIndex != null && accountIndex != transfer.accountIndex) return false

        when (transfer) {
            is MoneroIncomingTransfer -> {
                if (hasDestinations != null) return false
                if (address != null && address != transfer.address) return false
                if (!addresses.isNullOrEmpty() && transfer.address !in addresses!!) return false
                if (subaddressIndex != null && subaddressIndex != transfer.subaddressIndex) return false
                if (!subaddressIndices.isNullOrEmpty() && transfer.subaddressIndex !in subaddressIndices!!) return false
            }
            is MoneroOutgoingTransfer -> {
                val transferAddresses = transfer.addresses
                if (address != null && (transferAddresses.isNullOrEmpty() || address !in transferAddresses)) return false
                if (!addresses.isNullOrEmpty() && (transferAddresses.isNullOrEmpty() || addresses!!.none { it in transferAddresses })) return false
                val transferIndices = transfer.subaddressIndices
                if (subaddressIndex != null && (transferIndices.isNullOrEmpty() || subaddressIndex !in transferIndices)) return false
                if (!subaddressIndices.isNullOrEmpty() && (transferIndices.isNullOrEmpty() || subaddressIndices!!.none { it in transferIndices })) return false
                if (hasDestinations != null && hasDestinations != !transfer.destinations.isNullOrEmpty()) return false
            }
            else -> throw MoneroError("Transfer must be MoneroIncomingTransfer or MoneroOutgoingTransfer")
        }

        // filter with the query of the tx
        val query = txQuery
        if (queryParent && query != null) {
            val tx = transfer.tx ?: return false
            if (!query.meetsCriteria(tx, false)) return false
        }
        return true
    }
}
