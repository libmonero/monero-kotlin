package monero.wallet.model

import monero.daemon.model.MoneroOutput
import monero.internal.Reconcile

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

    /**
     * Merge another copy of the same output into this one, as [MoneroOutput.merge] does, and the account, the subaddress and whether it
     * is spent or frozen.
     *
     * @param other is the output to merge into this one. One that is not an output of a wallet merges as an output
     * @throws MoneroError if the two have a different value in a field that does not change
     */
    override fun merge(other: MoneroOutput) {
        if (this === other) return
        super.merge(other)
        if (other !is MoneroOutputWallet) return
        accountIndex = Reconcile.value(accountIndex, other.accountIndex, "output wallet accountIndex")
        subaddressIndex = Reconcile.value(subaddressIndex, other.subaddressIndex, "output wallet subaddressIndex")
        isSpent = Reconcile.value(isSpent, other.isSpent, "output wallet isSpent")
        isFrozen = Reconcile.value(isFrozen, other.isFrozen, "output wallet isFrozen")
    }
}
