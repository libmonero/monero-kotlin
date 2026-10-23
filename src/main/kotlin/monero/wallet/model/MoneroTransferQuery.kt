package monero.wallet.model

/**
 * Configures a query for the transfers of a wallet: all of them are returned, except the ones that do not meet the
 * criteria that are set. The query of the transaction of the transfers is in txQuery.
 */
class MoneroTransferQuery : MoneroTransfer {

    // true to match the incoming transfers, false to match the outgoing one, null to match both
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
}
