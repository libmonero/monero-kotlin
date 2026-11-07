package monero.wallet.model

import java.math.BigInteger

/**
 * Configures a query for the outputs of a wallet: all of them are returned, except the ones that do not meet the criteria that are set. The query of the transaction of the outputs is in txQuery.
 */
class MoneroOutputQuery : MoneroOutputWallet {
    var subaddressIndices: List<Int>? = null
    var minAmount: BigInteger? = null
    var maxAmount: BigInteger? = null
    var txQuery: MoneroTxQuery? = null

    constructor() : super()

    constructor(other: MoneroOutputQuery) : super(other) {
        this.subaddressIndices = other.subaddressIndices?.let { ArrayList(it) }
        this.minAmount = other.minAmount
        this.maxAmount = other.maxAmount
        this.txQuery = other.txQuery?.copy()
    }

    override fun copy(): MoneroOutputQuery {
        return MoneroOutputQuery(this)
    }

    /**
     * Indicates if an output of a wallet meets the criteria of this query: every field of the query that is set has to be the same in
     * the output, which has to have it, the key image has to have the hex and the signature that the query has, the subaddress index
     * one of the subaddress indices, and the amount between the minimum and the maximum.
     *
     * @param output is the output to check
     * @param queryParent indicates if the query of the transaction of the output is checked too
     * @return true if the output meets the criteria
     */
    fun meetsCriteria(output: MoneroOutputWallet, queryParent: Boolean = true): Boolean {

        // filter on the output
        if (accountIndex != null && accountIndex != output.accountIndex) return false
        if (subaddressIndex != null && subaddressIndex != output.subaddressIndex) return false
        if (amount != null && amount != output.amount) return false
        if (isSpent != null && isSpent != output.isSpent) return false
        if (isFrozen != null && isFrozen != output.isFrozen) return false

        // filter on the key image
        val queryKeyImage = keyImage
        if (queryKeyImage != null) {
            val outputKeyImage = output.keyImage ?: return false
            if (queryKeyImage.hex != null && queryKeyImage.hex != outputKeyImage.hex) return false
            if (queryKeyImage.signature != null && queryKeyImage.signature != outputKeyImage.signature) return false
        }

        // filter on the extensions
        if (!subaddressIndices.isNullOrEmpty() && output.subaddressIndex !in subaddressIndices!!) return false
        val outputAmount = output.amount
        if (minAmount != null && (outputAmount == null || outputAmount < minAmount)) return false
        if (maxAmount != null && (outputAmount == null || outputAmount > maxAmount)) return false

        // filter with the query of the tx
        val query = txQuery
        if (queryParent && query != null) {
            val tx = output.getTxWallet() ?: return false
            if (!query.meetsCriteria(tx, false)) return false
        }
        return true
    }
}
