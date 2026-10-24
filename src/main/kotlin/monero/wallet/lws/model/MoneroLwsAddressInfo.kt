package monero.wallet.lws.model

import java.math.BigInteger

// what a light wallet wants out of get_address_info: its balances and
// where its scan is. total balance is received minus sent, unlocked is
// that minus the locked funds
class MoneroLwsAddressInfo {
    var totalBalance: BigInteger? = null        // atomic units
    var unlockedBalance: BigInteger? = null
    var scannedHeight: Long? = null

    // the scan start comes from get_address_info, not login. the spec
    // is stale on this lol
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
