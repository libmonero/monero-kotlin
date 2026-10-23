package monero.wallet.model

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
}
