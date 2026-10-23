package monero.wallet.model

import java.math.BigInteger

/**
 * Models an account of a wallet.
 */
class MoneroAccount {
    var index: Int? = null
    var primaryAddress: String? = null
    var balance: BigInteger? = null
    var unlockedBalance: BigInteger? = null
    var tag: String? = null
    var subaddresses: List<MoneroSubaddress>? = null

    constructor()

    constructor(other: MoneroAccount) {
        this.index = other.index
        this.primaryAddress = other.primaryAddress
        this.balance = other.balance
        this.unlockedBalance = other.unlockedBalance
        this.tag = other.tag
        this.subaddresses = other.subaddresses?.map { it.copy() }
    }

    fun copy(): MoneroAccount {
        return MoneroAccount(this)
    }
}
