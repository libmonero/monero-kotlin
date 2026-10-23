package monero.daemon.model

/**
 * Models the result of getting blocks by hash.
 */
class MoneroGetBlocksByHashResult {
    // height of the chain of the daemon when it answered
    var currentHeight: Long? = null
    var blocks: List<MoneroBlock>? = null

    constructor()

    constructor(other: MoneroGetBlocksByHashResult) {
        this.currentHeight = other.currentHeight
        this.blocks = other.blocks?.map { it.copy() }
    }

    fun copy(): MoneroGetBlocksByHashResult {
        return MoneroGetBlocksByHashResult(this)
    }
}
