package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter
import java.math.BigInteger
import monero.internal.Reconcile

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

    /**
     * Merge another copy of the same transaction into this one: a field that is set on one side is taken, and the same value on both
     * is kept. A transaction can become confirmed, relayed, failed or seen as a double spend, and it can become unlocked, so those
     * take the value that it changes to, and the number of confirmations is the higher of the two. A transaction in a block is
     * not in the pool, and has no times of being received or relayed; one that is not is in the pool if either is, with the earliest
     * time that it was received and the latest that it was relayed.
     *
     * If the two are in blocks that are not the same block, the blocks are merged, which comes back to merge the transactions of the
     * blocks that have the same hash. The inputs with the same key image are merged, and the outputs with the same key image or
     * stealth public key; the others are added. The inputs and the outputs of the other are in this transaction afterwards.
     *
     * @param other is the transaction to merge into this one
     * @throws MoneroError if a field that does not change has a different value in the two
     */
    open fun merge(other: MoneroTx) {
        if (this === other) return

        // merge the blocks if they are not the same, which comes back to merging the transactions
        val otherBlock = other.block
        if (block !== otherBlock) {
            if (block == null) {
                block = otherBlock
                // the block of the other has this transaction in its place
                otherBlock!!.txs = otherBlock.txs?.map { if (it === other) this else it }
            } else if (otherBlock != null) {
                block!!.merge(otherBlock)
                return
            }
        }

        hash = Reconcile.value(hash, other.hash, "tx hash")
        version = Reconcile.value(version, other.version, "tx version")
        paymentId = Reconcile.value(paymentId, other.paymentId, "tx paymentId")
        fee = Reconcile.value(fee, other.fee, "tx fee")
        ringSize = Reconcile.value(ringSize, other.ringSize, "tx ringSize")
        isConfirmed = Reconcile.flag(isConfirmed, other.isConfirmed, true)
        isMinerTx = Reconcile.value(isMinerTx, other.isMinerTx, "tx isMinerTx")
        relay = Reconcile.flag(relay, other.relay, true)
        isRelayed = Reconcile.flag(isRelayed, other.isRelayed, true)
        isDoubleSpendSeen = Reconcile.flag(isDoubleSpendSeen, other.isDoubleSpendSeen, true)
        isLocked = Reconcile.flag(isLocked, other.isLocked, false)
        key = Reconcile.value(key, other.key, "tx key")
        fullHex = Reconcile.value(fullHex, other.fullHex, "tx fullHex")
        prunedHex = Reconcile.value(prunedHex, other.prunedHex, "tx prunedHex")
        prunableHex = Reconcile.value(prunableHex, other.prunableHex, "tx prunableHex")
        prunableHash = Reconcile.value(prunableHash, other.prunableHash, "tx prunableHash")
        size = Reconcile.value(size, other.size, "tx size")
        weight = Reconcile.value(weight, other.weight, "tx weight")
        outputIndices = Reconcile.list(outputIndices, other.outputIndices, "tx outputIndices")
        metadata = Reconcile.value(metadata, other.metadata, "tx metadata")
        commonTxSets = Reconcile.value(commonTxSets, other.commonTxSets, "tx commonTxSets")
        extra = Reconcile.list(extra, other.extra, "tx extra")
        rctSignatures = Reconcile.value(rctSignatures, other.rctSignatures, "tx rctSignatures")
        rctSigPrunable = Reconcile.value(rctSigPrunable, other.rctSigPrunable, "tx rctSigPrunable")
        isKeptByBlock = Reconcile.value(isKeptByBlock, other.isKeptByBlock, "tx isKeptByBlock")
        isFailed = Reconcile.flag(isFailed, other.isFailed, true)
        lastFailedHeight = Reconcile.value(lastFailedHeight, other.lastFailedHeight, "tx lastFailedHeight")
        lastFailedHash = Reconcile.value(lastFailedHash, other.lastFailedHash, "tx lastFailedHash")
        maxUsedBlockHeight = Reconcile.value(maxUsedBlockHeight, other.maxUsedBlockHeight, "tx maxUsedBlockHeight")
        maxUsedBlockHash = Reconcile.value(maxUsedBlockHash, other.maxUsedBlockHash, "tx maxUsedBlockHash")
        signatures = Reconcile.list(signatures, other.signatures, "tx signatures")
        unlockTime = Reconcile.value(unlockTime, other.unlockTime, "tx unlockTime")
        numConfirmations = Reconcile.max(numConfirmations, other.numConfirmations)

        // an input is the same as another if it spends the same key image
        val otherInputs = other.inputs
        if (!otherInputs.isNullOrEmpty()) {
            val merged = ArrayList(inputs ?: emptyList())
            for (input in otherInputs) {
                input.tx = this
                val keyImage = input.keyImage?.hex
                val same = if (keyImage == null) null else merged.firstOrNull { it.keyImage?.hex == keyImage }
                if (same == null) merged.add(input) else same.merge(input)
            }
            inputs = merged
        }

        // an output is the same as another if it has the same key image or the same stealth public key, and any other is added
        val otherOutputs = other.outputs
        if (!otherOutputs.isNullOrEmpty()) {
            val merged = ArrayList(outputs ?: emptyList())
            for (output in otherOutputs) {
                output.tx = this
                val keyImage = output.keyImage?.hex
                val stealthPublicKey = output.stealthPublicKey
                val same = merged.firstOrNull {
                    (keyImage != null && it.keyImage?.hex == keyImage) || (stealthPublicKey != null && it.stealthPublicKey == stealthPublicKey)
                }
                if (same == null) merged.add(output) else same.merge(output)
            }
            outputs = merged
        }

        // unrelayed -> relayed -> confirmed
        if (isConfirmed == true) {
            inTxPool = false
            receivedTimestamp = null
            lastRelayedTimestamp = null
        } else {
            inTxPool = Reconcile.flag(inTxPool, other.inTxPool, true)
            receivedTimestamp = Reconcile.min(receivedTimestamp, other.receivedTimestamp)
            lastRelayedTimestamp = Reconcile.max(lastRelayedTimestamp, other.lastRelayedTimestamp)
        }
    }

    companion object {

        /**
         * The payment id of a transaction that has none: eight bytes of zeros, as hexadecimal.
         */
        const val DEFAULT_PAYMENT_ID = "0000000000000000"
    }
}
