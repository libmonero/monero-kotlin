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
}
