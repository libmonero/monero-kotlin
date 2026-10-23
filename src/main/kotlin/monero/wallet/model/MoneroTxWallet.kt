package monero.wallet.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.math.BigInteger
import monero.daemon.model.MoneroOutput
import monero.daemon.model.MoneroTx

/**
 * Models a Monero transaction in the context of a wallet: the transfers that it has for the wallet, and the outputs
 * that the wallet spent or received in it.
 */
open class MoneroTxWallet : MoneroTx {

    // not part of the JSON, since the set holds its transactions. A set sets it when the transactions are assigned to it
    @JsonIgnore
    var txSet: MoneroTxSet? = null
    var isIncoming: Boolean? = null
    var isOutgoing: Boolean? = null

    /**
     * Incoming transfers of the transaction, one for each subaddress of the wallet that receives. Assigning the list
     * sets the transaction of each transfer.
     */
    @set:JsonSetter("incomingTransfers")
    var incomingTransfers: List<MoneroIncomingTransfer>? = null
        set(value) {
            field = value
            value?.forEach { it.tx = this }
        }

    /**
     * Outgoing transfer of the transaction. Assigning it sets the transaction of the transfer.
     */
    @set:JsonSetter("outgoingTransfer")
    var outgoingTransfer: MoneroOutgoingTransfer? = null
        set(value) {
            field = value
            value?.tx = this
        }
    var note: String? = null

    // the wallet knows these for a transaction that it creates
    var inputSum: BigInteger? = null
    var outputSum: BigInteger? = null
    var changeAddress: String? = null
    var changeAmount: BigInteger? = null
    var numDummyOutputs: Int? = null
    var extraHex: String? = null

    @set:JsonDeserialize(contentAs = MoneroOutputWallet::class)
    @set:JsonSetter("inputs")
    override var inputs: List<MoneroOutput>?
        get() = super.inputs
        set(value) {
            super.inputs = value
        }

    @set:JsonDeserialize(contentAs = MoneroOutputWallet::class)
    @set:JsonSetter("outputs")
    override var outputs: List<MoneroOutput>?
        get() = super.outputs
        set(value) {
            super.outputs = value
        }

    constructor() : super()

    /**
     * Construct this transaction as a deep copy of the given transaction.
     *
     * @param tx is the transaction to make a deep copy of
     */
    @Suppress("LeakingThis")
    constructor(tx: MoneroTxWallet) : super(tx) {
        this.isIncoming = tx.isIncoming
        this.isOutgoing = tx.isOutgoing
        this.note = tx.note
        this.inputSum = tx.inputSum
        this.outputSum = tx.outputSum
        this.changeAddress = tx.changeAddress
        this.changeAmount = tx.changeAmount
        this.numDummyOutputs = tx.numDummyOutputs
        this.extraHex = tx.extraHex
        this.incomingTransfers = tx.incomingTransfers?.map { it.copy() }
        this.outgoingTransfer = tx.outgoingTransfer?.copy()
    }

    override fun copy(): MoneroTxWallet {
        return MoneroTxWallet(this)
    }

    /**
     * Get the inputs that the wallet spent in the transaction.
     *
     * @return the inputs of the transaction, as wallet outputs
     */
    fun getInputsWallet(): List<MoneroOutputWallet> {
        return inputs?.filterIsInstance<MoneroOutputWallet>() ?: emptyList()
    }

    /**
     * Get the outputs that the wallet received in the transaction.
     *
     * @return the outputs of the transaction, as wallet outputs
     */
    fun getOutputsWallet(): List<MoneroOutputWallet> {
        return outputs?.filterIsInstance<MoneroOutputWallet>() ?: emptyList()
    }

    /**
     * Get the transfers of the transaction: the outgoing transfer, then the incoming ones.
     *
     * @return the transfers of the transaction
     */
    fun getTransfers(): List<MoneroTransfer> {
        val transfers = ArrayList<MoneroTransfer>()
        outgoingTransfer?.let { transfers.add(it) }
        incomingTransfers?.let { transfers.addAll(it) }
        return transfers
    }

    /**
     * Get the total amount that the wallet receives in the transaction.
     *
     * @return the sum of the incoming transfers (atomic units)
     */
    fun getIncomingAmount(): BigInteger {
        var total = BigInteger.ZERO
        incomingTransfers?.forEach { transfer -> transfer.amount?.let { total = total.add(it) } }
        return total
    }

    /**
     * Get the total amount that the wallet sends in the transaction.
     *
     * @return the amount of the outgoing transfer (atomic units)
     */
    fun getOutgoingAmount(): BigInteger {
        return outgoingTransfer?.amount ?: BigInteger.ZERO
    }
}
