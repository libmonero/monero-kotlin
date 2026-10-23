package monero.wallet.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger

/**
 * Models a base transfer of funds to or from the wallet. It is in its transaction, which is in its block.
 */
abstract class MoneroTransfer {

    // not part of the JSON, since the transaction holds its transfers. A transaction sets it when the transfers are assigned to it
    @JsonIgnore
    var tx: MoneroTxWallet? = null
    var amount: BigInteger? = null
    var accountIndex: Int? = null

    /**
     * Indicates if the transfer is incoming, null if it is a query that matches both.
     */
    abstract val isIncoming: Boolean?

    val isOutgoing: Boolean?
        get() = isIncoming?.not()

    constructor()

    constructor(transfer: MoneroTransfer) {
        this.amount = transfer.amount
        this.accountIndex = transfer.accountIndex
    }

    abstract fun copy(): MoneroTransfer
}
