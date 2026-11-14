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

    // height of the block of the transactions to match. A transaction that is not in a block yet has no height, and
    // does not meet a minHeight: monero-java has it meet every one
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

    /**
     * Indicates if a transaction of a wallet meets the criteria of this query: every field of the query that is set has to be the
     * same in the transaction, the hash has to be one of the hashes, the payment id one of the payment ids, and the height of its block
     * the height, and between the minimum and the maximum, so a transaction that is not in a block never meets a query by height.
     *
     * @param tx is the transaction to check
     * @param queryChildren indicates if the transfer query, the input query and the output query are checked too: the transaction
     *        has to have a transfer, an input and an output that meets each of the ones that are set
     * @return true if the transaction meets the criteria
     */
    fun meetsCriteria(tx: MoneroTxWallet, queryChildren: Boolean = true): Boolean {

        // filter on the fields of the tx
        if (hash != null && hash != tx.hash) return false
        if (paymentId != null && paymentId != tx.paymentId) return false
        if (isConfirmed != null && isConfirmed != tx.isConfirmed) return false
        if (inTxPool != null && inTxPool != tx.inTxPool) return false
        if (relay != null && relay != tx.relay) return false
        if (isRelayed != null && isRelayed != tx.isRelayed) return false
        if (isFailed != null && isFailed != tx.isFailed) return false
        if (isMinerTx != null && isMinerTx != tx.isMinerTx) return false
        if (isLocked != null && isLocked != tx.isLocked) return false
        if (hasPaymentId != null && hasPaymentId != (tx.paymentId != null)) return false
        if (isIncoming != null && isIncoming != tx.isIncoming) return false
        if (isOutgoing != null && isOutgoing != tx.isOutgoing) return false

        // filter on the remaining fields
        val txHeight = tx.height
        if (!hashes.isNullOrEmpty() && tx.hash !in hashes!!) return false
        if (!paymentIds.isNullOrEmpty() && tx.paymentId !in paymentIds!!) return false
        if (height != null && txHeight != height) return false
        if (minHeight != null && (txHeight == null || txHeight < minHeight!!)) return false
        if (maxHeight != null && (txHeight == null || txHeight > maxHeight!!)) return false

        if (!queryChildren) return true

        // at least one transfer must meet the transfer query
        val transfers = transferQuery
        if (transfers != null && tx.getTransfers().none { transfers.meetsCriteria(it, false) }) return false

        // at least one input must meet the input query, and one output the output query
        val inputs = inputQuery
        if (inputs != null && tx.getInputsWallet().none { inputs.meetsCriteria(it, false) }) return false
        val outputs = outputQuery
        if (outputs != null && tx.getOutputsWallet().none { outputs.meetsCriteria(it, false) }) return false

        return true
    }

    /**
     * Remove from this query what depends on the transactions around the one that it queries: whether it is incoming or outgoing, and
     * the transfer query, the input query and the output query. What is left can be asked of a transaction by itself.
     *
     * @return this query
     */
    fun decontextualize(): MoneroTxQuery {
        isIncoming = null
        isOutgoing = null
        transferQuery = null
        inputQuery = null
        outputQuery = null
        return this
    }
}
