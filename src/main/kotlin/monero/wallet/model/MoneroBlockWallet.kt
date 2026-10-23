package monero.wallet.model

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import monero.daemon.model.MoneroBlock
import monero.daemon.model.MoneroTx

/**
 * Models a block that holds transactions of a wallet. The wallet does not have the hash of the block, only its height
 * and its time, and the block without a height holds the transactions that no block holds yet.
 */
class MoneroBlockWallet : MoneroBlock {

    @set:JsonDeserialize(contentAs = MoneroTxWallet::class)
    @set:JsonSetter("txs")
    override var txs: List<MoneroTx>?
        get() = super.txs
        set(value) {
            super.txs = value
        }

    constructor() : super()

    constructor(block: MoneroBlockWallet) : super(block)

    override fun copy(): MoneroBlockWallet {
        return MoneroBlockWallet(this)
    }

    /**
     * Get the transactions of the block.
     *
     * @return the transactions of the block, as wallet transactions
     */
    fun getTxsWallet(): List<MoneroTxWallet> {
        return txs?.filterIsInstance<MoneroTxWallet>() ?: emptyList()
    }
}
