package monero.wallet.model

import monero.daemon.model.MoneroNetworkType

/**
 * Models an address that was decoded: its type, and the network that it is for.
 */
class MoneroDecodedAddress {
    var address: String? = null
    var addressType: MoneroAddressType? = null
    var networkType: MoneroNetworkType? = null

    constructor()

    /**
     * Construct a decoded address.
     *
     * @param address is the address
     * @param addressType is the type of the address
     * @param networkType is the network that the address is for
     */
    constructor(address: String?, addressType: MoneroAddressType?, networkType: MoneroNetworkType?) {
        this.address = address
        this.addressType = addressType
        this.networkType = networkType
    }

    constructor(other: MoneroDecodedAddress) {
        this.address = other.address
        this.addressType = other.addressType
        this.networkType = other.networkType
    }

    fun copy(): MoneroDecodedAddress {
        return MoneroDecodedAddress(this)
    }
}
