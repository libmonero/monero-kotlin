package monero.daemon.model

/**
 * Models the result of getting block hashes.
 */
class MoneroGetBlockHashesResult {
    // height of the first hash
    var startHeight: Long? = null
    // height of the chain of the daemon when it answered
    var currentHeight: Long? = null
    var hashes: List<String>? = null

    constructor()

    constructor(other: MoneroGetBlockHashesResult) {
        this.startHeight = other.startHeight
        this.currentHeight = other.currentHeight
        this.hashes = other.hashes?.let { ArrayList(it) }
    }

    fun copy(): MoneroGetBlockHashesResult {
        return MoneroGetBlockHashesResult(this)
    }
}
