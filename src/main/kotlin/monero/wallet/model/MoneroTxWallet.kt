package monero.wallet.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.math.BigInteger
import monero.daemon.model.MoneroOutput
import monero.daemon.model.MoneroTx
import monero.internal.Reconcile

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
     * @param query is the query that the inputs have to meet, or null for all of them
     * @return the inputs of the transaction, as wallet outputs, that meet the query
     */
    fun getInputsWallet(query: MoneroOutputQuery? = null): List<MoneroOutputWallet> {
        val inputs = inputs?.filterIsInstance<MoneroOutputWallet>() ?: return emptyList()
        return if (query == null) inputs else inputs.filter { query.meetsCriteria(it) }
    }

    /**
     * Remove from the transaction the inputs that do not meet a query. The inputs that are not outputs of a wallet are removed too.
     *
     * @param query is the query that the inputs have to meet
     * @return the inputs that are left, as wallet outputs
     */
    fun filterInputsWallet(query: MoneroOutputQuery): List<MoneroOutputWallet> {
        val kept = getInputsWallet(query)
        inputs = kept
        return kept
    }

    /**
     * Get the outputs that the wallet received in the transaction.
     *
     * @param query is the query that the outputs have to meet, or null for all of them
     * @return the outputs of the transaction, as wallet outputs, that meet the query
     */
    fun getOutputsWallet(query: MoneroOutputQuery? = null): List<MoneroOutputWallet> {
        val outputs = outputs?.filterIsInstance<MoneroOutputWallet>() ?: return emptyList()
        return if (query == null) outputs else outputs.filter { query.meetsCriteria(it) }
    }

    /**
     * Remove from the transaction the outputs that do not meet a query. The outputs that are not outputs of a wallet are removed too.
     *
     * @param query is the query that the outputs have to meet
     * @return the outputs that are left, as wallet outputs
     */
    fun filterOutputsWallet(query: MoneroOutputQuery): List<MoneroOutputWallet> {
        val kept = getOutputsWallet(query)
        outputs = kept
        return kept
    }

    /**
     * Get the transfers of the transaction: the outgoing transfer, then the incoming ones.
     *
     * @param query is the query that the transfers have to meet, or null for all of them
     * @return the transfers of the transaction that meet the query
     */
    fun getTransfers(query: MoneroTransferQuery? = null): List<MoneroTransfer> {
        val transfers = ArrayList<MoneroTransfer>()
        outgoingTransfer?.let { if (query == null || query.meetsCriteria(it)) transfers.add(it) }
        incomingTransfers?.forEach { if (query == null || query.meetsCriteria(it)) transfers.add(it) }
        return transfers
    }

    /**
     * Remove from the transaction the transfers that do not meet a query: the outgoing transfer is removed, and so are the incoming
     * ones.
     *
     * @param query is the query that the transfers have to meet
     * @return the transfers that are left: the outgoing transfer, then the incoming ones
     */
    fun filterTransfers(query: MoneroTransferQuery): List<MoneroTransfer> {
        val kept = getTransfers(query)
        outgoingTransfer = kept.firstOrNull { it is MoneroOutgoingTransfer } as MoneroOutgoingTransfer?
        incomingTransfers = kept.filterIsInstance<MoneroIncomingTransfer>()
        return kept
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

    /**
     * Merge another copy of the same transaction into this one, as [MoneroTx.merge] does, and its transfers: the incoming transfers
     * of the account and the subaddress that this one has are merged, and the others are added, so one without an account or a
     * subaddress is always added, and the outgoing transfer is merged or taken. The transfers of the other are in this transaction
     * afterwards.
     *
     * @param other is the transaction to merge into this one. One that is not a transaction of a wallet merges as a transaction
     * @throws MoneroError if the two have a different value in a field that does not change
     */
    override fun merge(other: MoneroTx) {
        if (this === other) return
        super.merge(other)
        if (other !is MoneroTxWallet) return

        val otherIncoming = other.incomingTransfers
        if (!otherIncoming.isNullOrEmpty()) {
            val merged = ArrayList(incomingTransfers ?: emptyList())
            for (transfer in otherIncoming) {
                transfer.tx = this
                val same = merged.firstOrNull {
                    it === transfer || (it.accountIndex != null && it.subaddressIndex != null &&
                        it.accountIndex == transfer.accountIndex && it.subaddressIndex == transfer.subaddressIndex)
                }
                if (same == null) merged.add(transfer) else same.merge(transfer)
            }
            incomingTransfers = merged
        }

        val otherOutgoing = other.outgoingTransfer
        if (otherOutgoing != null) {
            otherOutgoing.tx = this
            val outgoing = outgoingTransfer
            if (outgoing == null) outgoingTransfer = otherOutgoing else outgoing.merge(otherOutgoing)
        }

        isIncoming = Reconcile.value(isIncoming, other.isIncoming, "tx wallet isIncoming")
        isOutgoing = Reconcile.value(isOutgoing, other.isOutgoing, "tx wallet isOutgoing")
        note = Reconcile.value(note, other.note, "tx wallet note")
        inputSum = Reconcile.value(inputSum, other.inputSum, "tx wallet inputSum")
        outputSum = Reconcile.value(outputSum, other.outputSum, "tx wallet outputSum")
        changeAddress = Reconcile.value(changeAddress, other.changeAddress, "tx wallet changeAddress")
        changeAmount = Reconcile.value(changeAmount, other.changeAmount, "tx wallet changeAmount")
        numDummyOutputs = Reconcile.value(numDummyOutputs, other.numDummyOutputs, "tx wallet numDummyOutputs")
        extraHex = Reconcile.value(extraHex, other.extraHex, "tx wallet extraHex")
    }
}
