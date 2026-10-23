package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter
import java.math.BigInteger

/**
 * Represents a transaction on the Monero network. It is in its block, which is the one that holds it in the chain, or
 * the one without a height that holds the transactions of the pool.
 */
open class MoneroTx {

    // not part of the JSON, since the block holds its transactions. A block sets it when the transactions are assigned to it
    @JsonIgnore
    var block: MoneroBlock? = null
    var hash: String? = null
    var version: Int? = null
    var isMinerTx: Boolean? = null
    var paymentId: String? = null
    var fee: BigInteger? = null
    var ringSize: Int? = null
    var relay: Boolean? = null
    var isRelayed: Boolean? = null
    var isConfirmed: Boolean? = null
    var inTxPool: Boolean? = null
    var isLocked: Boolean? = null
    var numConfirmations: Long? = null
    var unlockTime: BigInteger? = null
    var lastRelayedTimestamp: Long? = null
    var receivedTimestamp: Long? = null
    var isDoubleSpendSeen: Boolean? = null
    var key: String? = null
    var fullHex: String? = null
    var prunedHex: String? = null
    var prunableHex: String? = null
    var prunableHash: String? = null
    var size: Long? = null
    var weight: Long? = null

    /**
     * Inputs used in the transaction. Assigning the list sets the transaction of each input. A transaction of a wallet
     * overrides it, so that its inputs are read as wallet outputs.
     */
    @set:JsonSetter("inputs")
    open var inputs: List<MoneroOutput>? = null
        set(value) {
            field = value
            value?.forEach { it.tx = this }
        }

    /**
     * Outputs created by the transaction. Assigning the list sets the transaction of each output. A transaction of a
     * wallet overrides it, so that its outputs are read as wallet outputs.
     */
    @set:JsonSetter("outputs")
    open var outputs: List<MoneroOutput>? = null
        set(value) {
            field = value
            value?.forEach { it.tx = this }
        }
    var outputIndices: List<Long>? = null
    var metadata: String? = null
    var commonTxSets: String? = null

    // the bytes of the extra field of the transaction, as numbers from 0 to 255
    var extra: List<Int>? = null
    var rctSignatures: String? = null
    var rctSigPrunable: String? = null
    var isKeptByBlock: Boolean? = null
    var isFailed: Boolean? = null
    var lastFailedHeight: Long? = null
    var lastFailedHash: String? = null
    var maxUsedBlockHeight: Long? = null
    var maxUsedBlockHash: String? = null
    var signatures: List<String>? = null

    /**
     * Height of the block of the transaction, if it is confirmed. A query of transactions overrides it with the height
     * to match.
     */
    open val height: Long?
        get() = block?.height

    constructor()

    /**
     * Construct this transaction as a deep copy of the given transaction. The copy is not in a block: the block that
     * holds it sets it.
     *
     * @param tx is the transaction to make a deep copy of
     */
    @Suppress("LeakingThis")
    constructor(tx: MoneroTx) {
        this.hash = tx.hash
        this.version = tx.version
        this.isMinerTx = tx.isMinerTx
        this.paymentId = tx.paymentId
        this.fee = tx.fee
        this.ringSize = tx.ringSize
        this.relay = tx.relay
        this.isRelayed = tx.isRelayed
        this.isConfirmed = tx.isConfirmed
        this.inTxPool = tx.inTxPool
        this.isLocked = tx.isLocked
        this.numConfirmations = tx.numConfirmations
        this.unlockTime = tx.unlockTime
        this.lastRelayedTimestamp = tx.lastRelayedTimestamp
        this.receivedTimestamp = tx.receivedTimestamp
        this.isDoubleSpendSeen = tx.isDoubleSpendSeen
        this.key = tx.key
        this.fullHex = tx.fullHex
        this.prunedHex = tx.prunedHex
        this.prunableHex = tx.prunableHex
        this.prunableHash = tx.prunableHash
        this.size = tx.size
        this.weight = tx.weight
        this.inputs = tx.inputs?.map { it.copy() }
        this.outputs = tx.outputs?.map { it.copy() }
        this.outputIndices = tx.outputIndices?.let { ArrayList(it) }
        this.metadata = tx.metadata
        this.commonTxSets = tx.commonTxSets
        this.extra = tx.extra?.let { ArrayList(it) }
        this.rctSignatures = tx.rctSignatures
        this.rctSigPrunable = tx.rctSigPrunable
        this.isKeptByBlock = tx.isKeptByBlock
        this.isFailed = tx.isFailed
        this.lastFailedHeight = tx.lastFailedHeight
        this.lastFailedHash = tx.lastFailedHash
        this.maxUsedBlockHeight = tx.maxUsedBlockHeight
        this.maxUsedBlockHash = tx.maxUsedBlockHash
        this.signatures = tx.signatures?.let { ArrayList(it) }
    }

    open fun copy(): MoneroTx {
        return MoneroTx(this)
    }

    companion object {

        /**
         * The payment id of a transaction that has none: eight bytes of zeros, as hexadecimal.
         */
        const val DEFAULT_PAYMENT_ID = "0000000000000000"
    }
}
