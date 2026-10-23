package monero.daemon.model

/**
 * Monero daemon connection span.
 */
class MoneroConnectionSpan {
    var connectionId: String? = null
    var numBlocks: Long? = null
    var remoteAddress: String? = null
    var rate: Long? = null
    var speed: Long? = null
    // total size in bytes of all blocks (including transactions) included in the span
    var size: Long? = null
    var startHeight: Long? = null

    constructor()

    constructor(other: MoneroConnectionSpan) {
        this.connectionId = other.connectionId
        this.numBlocks = other.numBlocks
        this.remoteAddress = other.remoteAddress
        this.rate = other.rate
        this.speed = other.speed
        this.size = other.size
        this.startHeight = other.startHeight
    }

    fun copy(): MoneroConnectionSpan {
        return MoneroConnectionSpan(this)
    }
}
