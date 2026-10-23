package monero.wallet.model

import monero.daemon.model.MoneroOutput

/**
 * Models an output of a wallet, which is an output that the wallet has or had the ability to spend. It is in its transaction, as an input when the wallet spent it and as an output when the wallet received it.
 */
open class MoneroOutputWallet : MoneroOutput {
    var accountIndex: Int? = null
    var subaddressIndex: Int? = null
    var isSpent: Boolean? = null
    var isFrozen: Boolean? = null

    constructor() : super()

    constructor(other: MoneroOutputWallet) : super(other) {
        this.accountIndex = other.accountIndex
        this.subaddressIndex = other.subaddressIndex
        this.isSpent = other.isSpent
        this.isFrozen = other.isFrozen
    }

    override fun copy(): MoneroOutputWallet {
        return MoneroOutputWallet(this)
    }

    /**
     * Get the transaction of this output as a wallet transaction.
     *
     * @return the transaction of this output, or null if it is not in one of a wallet
     */
    fun getTxWallet(): MoneroTxWallet? {
        return tx as? MoneroTxWallet
    }
}
