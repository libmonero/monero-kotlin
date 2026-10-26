package monero.wallet.lws.model

import java.math.BigInteger

/**
 * Models what a light wallet wants out of get_address_info: its balances and where its scan is. The total balance is
 * what the address received minus what it sent, and the unlocked balance is that minus the locked funds.
 */
class MoneroLwsAddressInfo {
    // the balances of the address, in atomic units
    var totalBalance: BigInteger? = null
    var unlockedBalance: BigInteger? = null
    var scannedHeight: Long? = null

    // the scan start, which comes from get_address_info and not from login: the OpenAPI spec still claims the login
    // response has it, which the server never writes
    var startHeight: Long? = null

    constructor()

    constructor(other: MoneroLwsAddressInfo) {
        this.totalBalance = other.totalBalance
        this.unlockedBalance = other.unlockedBalance
        this.scannedHeight = other.scannedHeight
        this.startHeight = other.startHeight
    }

    fun copy(): MoneroLwsAddressInfo {
        return MoneroLwsAddressInfo(this)
    }
}
