package monero.wallet.model

/**
 * Models the result of synchronizing a wallet.
 */
class MoneroSyncResult {
    var numBlocksFetched: Long? = null
    var receivedMoney: Boolean? = null

    constructor()

    constructor(other: MoneroSyncResult) {
        this.numBlocksFetched = other.numBlocksFetched
        this.receivedMoney = other.receivedMoney
    }

    fun copy(): MoneroSyncResult {
        return MoneroSyncResult(this)
    }
}
