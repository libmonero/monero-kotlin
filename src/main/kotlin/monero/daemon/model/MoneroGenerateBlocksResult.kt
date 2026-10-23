package monero.daemon.model

/**
 * Models the result of generating blocks, which a daemon in regtest mode does.
 */
class MoneroGenerateBlocksResult {
    // height of the chain after the blocks were generated
    var height: Long? = null
    var blockHashes: List<String>? = null

    constructor()

    constructor(other: MoneroGenerateBlocksResult) {
        this.height = other.height
        this.blockHashes = other.blockHashes?.let { ArrayList(it) }
    }

    fun copy(): MoneroGenerateBlocksResult {
        return MoneroGenerateBlocksResult(this)
    }
}
