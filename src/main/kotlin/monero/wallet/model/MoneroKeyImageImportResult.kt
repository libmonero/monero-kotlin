package monero.wallet.model

import java.math.BigInteger

/**
 * Models the result of importing key images in a wallet.
 */
class MoneroKeyImageImportResult {
    var height: Long? = null
    var spentAmount: BigInteger? = null
    var unspentAmount: BigInteger? = null

    constructor()

    constructor(other: MoneroKeyImageImportResult) {
        this.height = other.height
        this.spentAmount = other.spentAmount
        this.unspentAmount = other.unspentAmount
    }

    fun copy(): MoneroKeyImageImportResult {
        return MoneroKeyImageImportResult(this)
    }
}
