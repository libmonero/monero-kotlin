package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonSetter

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
}
