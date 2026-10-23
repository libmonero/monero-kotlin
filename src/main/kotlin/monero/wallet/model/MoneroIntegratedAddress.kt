package monero.wallet.model

/**
 * Models an integrated address: a standard address with a payment id.
 */
class MoneroIntegratedAddress {
    var standardAddress: String? = null
    var paymentId: String? = null
    var integratedAddress: String? = null

    constructor()

    constructor(other: MoneroIntegratedAddress) {
        this.standardAddress = other.standardAddress
        this.paymentId = other.paymentId
        this.integratedAddress = other.integratedAddress
    }

    fun copy(): MoneroIntegratedAddress {
        return MoneroIntegratedAddress(this)
    }
}
