package monero.wallet.model

import monero.common.MoneroError
import monero.internal.Reconcile

/**
 * Models an outgoing transfer of funds from the wallet.
 */
class MoneroOutgoingTransfer : MoneroTransfer {

    var subaddressIndices: List<Int>? = null
    var addresses: List<String>? = null

    // a wallet that only has the keys does not know them
    var destinations: List<MoneroDestination>? = null

    override val isIncoming: Boolean?
        get() = false

    constructor() : super()

    constructor(transfer: MoneroOutgoingTransfer) : super(transfer) {
        this.subaddressIndices = transfer.subaddressIndices?.let { ArrayList(it) }
        this.addresses = transfer.addresses?.let { ArrayList(it) }
        this.destinations = transfer.destinations?.map { it.copy() }
    }

    override fun copy(): MoneroOutgoingTransfer {
        return MoneroOutgoingTransfer(this)
    }

    /**
     * Merge another copy of the same transfer into this one, as [MoneroTransfer.merge] does. The destinations are the ones of the
     * other if this transfer has none, and if both have them they have to be the same.
     *
     * @param other is the transfer to merge into this one. One that is not an outgoing transfer merges as a transfer
     * @throws MoneroError if the two have a different value in a field that does not change, or different destinations
     */
    override fun merge(other: MoneroTransfer) {
        if (this === other) return
        super.merge(other)
        if (other !is MoneroOutgoingTransfer) return
        subaddressIndices = Reconcile.list(subaddressIndices, other.subaddressIndices, "outgoing transfer subaddressIndices")
        addresses = Reconcile.list(addresses, other.addresses, "outgoing transfer addresses")

        val thisDestinations = destinations
        val otherDestinations = other.destinations
        if (thisDestinations.isNullOrEmpty()) {
            if (!otherDestinations.isNullOrEmpty()) destinations = otherDestinations
        } else if (!otherDestinations.isNullOrEmpty()) {
            if (thisDestinations.size != otherDestinations.size) throw MoneroError("Destination vectors are different sizes")
            for (i in thisDestinations.indices) {
                if (thisDestinations[i].address != otherDestinations[i].address || thisDestinations[i].amount != otherDestinations[i].amount) {
                    throw MoneroError("Destination vectors are different")
                }
            }
        }
    }
}
