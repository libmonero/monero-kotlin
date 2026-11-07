package monero.wallet.model

import monero.internal.Reconcile

/**
 * Models an incoming transfer of funds to the wallet.
 */
class MoneroIncomingTransfer : MoneroTransfer {

    var subaddressIndex: Int? = null
    var address: String? = null
    var numSuggestedConfirmations: Long? = null

    override val isIncoming: Boolean?
        get() = true

    constructor() : super()

    constructor(transfer: MoneroIncomingTransfer) : super(transfer) {
        this.subaddressIndex = transfer.subaddressIndex
        this.address = transfer.address
        this.numSuggestedConfirmations = transfer.numSuggestedConfirmations
    }

    override fun copy(): MoneroIncomingTransfer {
        return MoneroIncomingTransfer(this)
    }

    /**
     * Merge another copy of the same transfer into this one, as [MoneroTransfer.merge] does. The number of suggested confirmations is
     * the lower of the two.
     *
     * @param other is the transfer to merge into this one. One that is not an incoming transfer merges as a transfer
     * @throws MoneroError if the two have a different value in a field that does not change
     */
    override fun merge(other: MoneroTransfer) {
        if (this === other) return
        super.merge(other)
        if (other !is MoneroIncomingTransfer) return
        subaddressIndex = Reconcile.value(subaddressIndex, other.subaddressIndex, "incoming transfer subaddressIndex")
        address = Reconcile.value(address, other.address, "incoming transfer address")
        numSuggestedConfirmations = Reconcile.min(numSuggestedConfirmations, other.numSuggestedConfirmations)
    }
}
