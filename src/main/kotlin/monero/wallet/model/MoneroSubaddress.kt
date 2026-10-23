package monero.wallet.model

import java.math.BigInteger

/**
 * Models a subaddress of an account of a wallet. The primary address of an account is its subaddress 0.
 */
class MoneroSubaddress {
    var accountIndex: Int? = null
    var index: Int? = null
    var address: String? = null
    var label: String? = null
    var balance: BigInteger? = null
    var unlockedBalance: BigInteger? = null
    var numUnspentOutputs: Long? = null
    var isUsed: Boolean? = null
    var numBlocksToUnlock: Long? = null

    constructor()

    constructor(other: MoneroSubaddress) {
        this.accountIndex = other.accountIndex
        this.index = other.index
        this.address = other.address
        this.label = other.label
        this.balance = other.balance
        this.unlockedBalance = other.unlockedBalance
        this.numUnspentOutputs = other.numUnspentOutputs
        this.isUsed = other.isUsed
        this.numBlocksToUnlock = other.numBlocksToUnlock
    }

    fun copy(): MoneroSubaddress {
        return MoneroSubaddress(this)
    }
}
