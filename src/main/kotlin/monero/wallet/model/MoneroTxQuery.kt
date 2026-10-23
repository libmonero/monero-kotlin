package monero.wallet.model

/**
 * Configures a query for the transactions of a wallet: all of them are returned, except the ones that do not meet the
 * criteria that are set. It has every field of a transaction of a wallet to match, and the fields to query the
 * transfers, the inputs and the outputs of the transactions.
 */
class MoneroTxQuery : MoneroTxWallet {

    var hashes: List<String>? = null
    var hasPaymentId: Boolean? = null
    var paymentIds: List<String>? = null

    // height of the block of the transactions to match
    override var height: Long? = null
    var minHeight: Long? = null
    var maxHeight: Long? = null
    var includeOutputs: Boolean? = null
    var transferQuery: MoneroTransferQuery? = null
    var inputQuery: MoneroOutputQuery? = null
    var outputQuery: MoneroOutputQuery? = null

    constructor() : super()

    constructor(query: MoneroTxQuery) : super(query) {
        this.hashes = query.hashes?.let { ArrayList(it) }
        this.hasPaymentId = query.hasPaymentId
        this.paymentIds = query.paymentIds?.let { ArrayList(it) }
        this.height = query.height
        this.minHeight = query.minHeight
        this.maxHeight = query.maxHeight
        this.includeOutputs = query.includeOutputs
        this.transferQuery = query.transferQuery?.copy()
        this.inputQuery = query.inputQuery?.copy()
        this.outputQuery = query.outputQuery?.copy()
    }

    override fun copy(): MoneroTxQuery {
        return MoneroTxQuery(this)
    }
}
