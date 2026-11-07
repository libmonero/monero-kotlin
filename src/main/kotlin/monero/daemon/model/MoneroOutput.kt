package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import monero.internal.Reconcile

/**
 * Models a Monero transaction output.
 */
open class MoneroOutput {

    // not part of the JSON, since the transaction holds its outputs. A transaction sets it when the outputs are assigned to it
    @JsonIgnore
    var tx: MoneroTx? = null
    var keyImage: MoneroKeyImage? = null
    var amount: BigInteger? = null
    var index: Long? = null
    var ringOutputIndices: List<Long>? = null
    var stealthPublicKey: String? = null
    var mask: String? = null

    constructor()

    /**
     * Construct this output as a deep copy of the given output. The copy is not in a transaction: the transaction that
     * holds it sets it.
     *
     * @param output is the output to make a deep copy of
     */
    constructor(output: MoneroOutput) {
        this.keyImage = output.keyImage?.copy()
        this.amount = output.amount
        this.index = output.index
        this.ringOutputIndices = output.ringOutputIndices?.let { ArrayList(it) }
        this.stealthPublicKey = output.stealthPublicKey
        this.mask = output.mask
    }

    open fun copy(): MoneroOutput {
        return MoneroOutput(this)
    }

    /**
     * Merge another copy of the same output into this one: a field that is set on one side is taken, and the same value on both is
     * kept, and the key images are merged. If the two are in different transactions, the transactions are merged, which comes back
     * to merge the outputs.
     *
     * @param other is the output to merge into this one
     * @throws MoneroError if the two have a different value in a field that does not change
     */
    open fun merge(other: MoneroOutput) {
        if (this === other) return

        // merge the transactions if they are not the same, which comes back to merging the outputs
        val thisTx = tx
        val otherTx = other.tx
        if (thisTx != null && otherTx != null && thisTx !== otherTx) {
            thisTx.merge(otherTx)
            return
        }

        val otherKeyImage = other.keyImage
        if (keyImage == null) keyImage = otherKeyImage
        else if (otherKeyImage != null) keyImage!!.merge(otherKeyImage)
        amount = Reconcile.value(amount, other.amount, "output amount")
        index = Reconcile.value(index, other.index, "output index")
        ringOutputIndices = Reconcile.list(ringOutputIndices, other.ringOutputIndices, "output ringOutputIndices")
        stealthPublicKey = Reconcile.value(stealthPublicKey, other.stealthPublicKey, "output stealthPublicKey")
        mask = Reconcile.value(mask, other.mask, "output mask")
    }
}
