package monero.wallet.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import monero.internal.Reconcile

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

    /**
     * Merge another copy of the same transfer into this one: a field that is set on one side is taken, and the same value on both is
     * kept. If the two are in different transactions, the transactions are merged, which comes back to merge the transfers.
     *
     * An amount of zero on one side and another amount on the other is kept as it is: monero-project returns a transfer of a failed
     * transaction of the pool with an amount of zero, and it is not a conflict.
     *
     * @param other is the transfer to merge into this one
     * @throws MoneroError if the two have a different value in a field that does not change
     */
    open fun merge(other: MoneroTransfer) {
        if (this === other) return

        // merge the transactions if they are not the same, which comes back to merging the transfers
        val thisTx = tx
        val otherTx = other.tx
        if (thisTx != null && otherTx != null && thisTx !== otherTx) {
            thisTx.merge(otherTx)
            return
        }

        accountIndex = Reconcile.value(accountIndex, other.accountIndex, "transfer accountIndex")
        val thisAmount = amount
        val otherAmount = other.amount
        val zeroAgainstAmount = thisAmount != null && otherAmount != null && thisAmount != otherAmount &&
            (thisAmount.signum() == 0 || otherAmount.signum() == 0)
        if (!zeroAgainstAmount) amount = Reconcile.value(thisAmount, otherAmount, "transfer amount")
    }
}
