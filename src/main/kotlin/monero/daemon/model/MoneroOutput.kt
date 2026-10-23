package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger

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
}
