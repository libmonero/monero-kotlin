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
