package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonSetter
import monero.internal.Reconcile

/**
 * Models a Monero block in the blockchain.
 */
open class MoneroBlock : MoneroBlockHeader {

    var hex: String? = null

    /**
     * The miner transaction of the block. Assigning it sets the block of the transaction.
     */
    @set:JsonSetter("minerTx")
    var minerTx: MoneroTx? = null
        set(value) {
            field = value
            value?.block = this
        }

    /**
     * Transactions included in the block. Assigning the list sets the block of each transaction. A block of a wallet
     * overrides it, so that its transactions are read as wallet transactions.
     */
    @set:JsonSetter("txs")
    open var txs: List<MoneroTx>? = null
        set(value) {
            field = value
            value?.forEach { it.block = this }
        }

    // hashes of the transactions of the block, when the transactions themselves are not fetched
    var txHashes: List<String>? = null

    constructor() : super()

    /**
     * Construct this block as a deep copy of the given block. The transactions are copied, and they are in the new block.
     *
     * @param block is the block to make a deep copy of
     */
    @Suppress("LeakingThis")
    constructor(block: MoneroBlock) : super(block) {
        this.hex = block.hex
        this.minerTx = block.minerTx?.copy()
        this.txs = block.txs?.map { it.copy() }
        this.txHashes = block.txHashes?.let { ArrayList(it) }
    }

    override fun copy(): MoneroBlock {
        return MoneroBlock(this)
    }

    /**
     * Merge another block into this one. The header is merged as [MoneroBlockHeader.merge] does, then the miner transaction and the
     * transactions: a transaction of the other block that has the hash of one of this one is merged into it, and any other is added,
     * so a transaction without a hash is always added. The transactions of the other block are in this one afterwards.
     *
     * @param other is the block to merge into this one. A header that is not a block merges as a header
     * @throws MoneroError if a field that does not change has a different value in the two
     */
    override fun merge(other: MoneroBlockHeader) {
        if (this === other) return
        super.merge(other)
        if (other !is MoneroBlock) return

        hex = Reconcile.value(hex, other.hex, "block hex")
        txHashes = Reconcile.list(txHashes, other.txHashes, "block txHashes")

        // the miner tx of the other block is this block's if it has none, and it is merged into it if it has one
        val otherMinerTx = other.minerTx
        if (minerTx == null) minerTx = otherMinerTx
        if (otherMinerTx != null) {
            otherMinerTx.block = this
            minerTx!!.merge(otherMinerTx)
        }

        val otherTxs = other.txs
        if (!otherTxs.isNullOrEmpty()) {
            val merged = ArrayList(txs ?: emptyList())
            for (otherTx in otherTxs) {
                otherTx.block = this
                val same = merged.firstOrNull { it === otherTx || (it.hash != null && it.hash == otherTx.hash) }
                if (same == null) merged.add(otherTx) else same.merge(otherTx)
            }
            txs = merged
        }
    }
}
