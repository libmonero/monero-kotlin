package monero.wallet.model

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
}
