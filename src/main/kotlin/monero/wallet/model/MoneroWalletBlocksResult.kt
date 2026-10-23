package monero.wallet.model

/**
 * Models the result of the calls that get the transactions, the transfers and the outputs of a wallet: {"blocks":[...]}. Each transaction is in its block, and each transfer and each output is in its transaction. The block without a height holds the transactions that no block holds yet.
 */
class MoneroWalletBlocksResult {
    var blocks: List<MoneroBlockWallet>? = null

    constructor()

    constructor(other: MoneroWalletBlocksResult) {
        this.blocks = other.blocks?.map { it.copy() }
    }

    fun copy(): MoneroWalletBlocksResult {
        return MoneroWalletBlocksResult(this)
    }

    /**
     * Get the transactions of the blocks, in the order of the blocks.
     *
     * @return the transactions of the blocks
     */
    fun getTxs(): List<MoneroTxWallet> {
        val txs = ArrayList<MoneroTxWallet>()
        blocks?.forEach { block -> txs.addAll(block.getTxsWallet()) }
        return txs
    }

    /**
     * Get the transfers of the transactions of the blocks.
     *
     * @return the transfers of the transactions
     */
    fun getTransfers(): List<MoneroTransfer> {
        return getTxs().flatMap { it.getTransfers() }
    }

    /**
     * Get the outputs that the wallet received in the transactions of the blocks.
     *
     * @return the outputs of the transactions
     */
    fun getOutputs(): List<MoneroOutputWallet> {
        return getTxs().flatMap { it.getOutputsWallet() }
    }

    /**
     * Get the inputs that the wallet spent in the transactions of the blocks.
     *
     * @return the inputs of the transactions
     */
    fun getInputs(): List<MoneroOutputWallet> {
        return getTxs().flatMap { it.getInputsWallet() }
    }
}
