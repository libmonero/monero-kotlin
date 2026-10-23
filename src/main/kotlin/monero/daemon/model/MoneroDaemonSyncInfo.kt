package monero.daemon.model

/**
 * Models daemon synchronization information.
 */
class MoneroDaemonSyncInfo : MoneroRpcPaymentInfo {
    var height: Long? = null
    var peers: List<MoneroPeer>? = null
    var spans: List<MoneroConnectionSpan>? = null
    var targetHeight: Long? = null
    var nextNeededPruningSeed: Int? = null
    var overview: String? = null

    constructor() : super()

    constructor(other: MoneroDaemonSyncInfo) : super(other) {
        this.height = other.height
        this.peers = other.peers?.map { it.copy() }
        this.spans = other.spans?.map { it.copy() }
        this.targetHeight = other.targetHeight
        this.nextNeededPruningSeed = other.nextNeededPruningSeed
        this.overview = other.overview
    }

    override fun copy(): MoneroDaemonSyncInfo {
        return MoneroDaemonSyncInfo(this)
    }
}
