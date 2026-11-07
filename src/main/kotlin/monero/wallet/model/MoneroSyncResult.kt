package monero.wallet.model

/**
 * Models the result of synchronizing a wallet.
 */
class MoneroSyncResult {
    var numBlocksFetched: Long? = null
    var receivedMoney: Boolean? = null

    constructor()

    /**
     * Construct the result of a sync.
     *
     * @param numBlocksFetched is the number of blocks that the sync fetched
     * @param receivedMoney indicates if the sync found that the wallet received money
     */
    constructor(numBlocksFetched: Long?, receivedMoney: Boolean?) {
        this.numBlocksFetched = numBlocksFetched
        this.receivedMoney = receivedMoney
    }

    constructor(other: MoneroSyncResult) {
        this.numBlocksFetched = other.numBlocksFetched
        this.receivedMoney = other.receivedMoney
    }

    fun copy(): MoneroSyncResult {
        return MoneroSyncResult(this)
    }
}
