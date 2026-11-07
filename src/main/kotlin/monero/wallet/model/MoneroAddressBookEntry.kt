package monero.wallet.model

/**
 * Models an entry of the address book of a wallet.
 */
class MoneroAddressBookEntry {
    var index: Long? = null
    var address: String? = null
    var description: String? = null
    var paymentId: String? = null

    constructor()

    /**
     * Construct an entry of the address book.
     *
     * @param index is the index of the entry
     * @param address is the address of the entry
     * @param description is the description of the entry
     * @param paymentId is the payment id of the entry
     */
    constructor(index: Long?, address: String?, description: String?, paymentId: String? = null) {
        this.index = index
        this.address = address
        this.description = description
        this.paymentId = paymentId
    }

    constructor(other: MoneroAddressBookEntry) {
        this.index = other.index
        this.address = other.address
        this.description = other.description
        this.paymentId = other.paymentId
    }

    fun copy(): MoneroAddressBookEntry {
        return MoneroAddressBookEntry(this)
    }
}
