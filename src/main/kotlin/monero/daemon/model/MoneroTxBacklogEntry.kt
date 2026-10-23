package monero.daemon.model

/**
 * Models an entry of the backlog of the transaction pool. monero-cpp has no field in it, and monero-c writes each entry as an empty JSON object, so the number of the entries is all that it tells.
 */
class MoneroTxBacklogEntry {

    constructor()

    constructor(other: MoneroTxBacklogEntry)

    fun copy(): MoneroTxBacklogEntry {
        return MoneroTxBacklogEntry(this)
    }
}
