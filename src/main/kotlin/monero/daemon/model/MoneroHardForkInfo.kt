package monero.daemon.model

/**
 * Models information regarding hard fork voting and readiness.
 */
class MoneroHardForkInfo : MoneroRpcPaymentInfo {
    // block height at which the hard fork would be enabled if voted in
    var earliestHeight: Long? = null
    var isEnabled: Boolean? = null
    // 0 (there is likely a hard fork), 1 (an update is needed to fork properly), 2 (everything looks good)
    var state: Int? = null
    // minimum percent of votes to trigger the hard fork
    var threshold: Int? = null
    // the major block version for the fork
    var version: Int? = null
    var numVotes: Int? = null
    // number of blocks over which the current votes are cast
    var window: Int? = null
    var voting: Int? = null

    constructor() : super()

    constructor(other: MoneroHardForkInfo) : super(other) {
        this.earliestHeight = other.earliestHeight
        this.isEnabled = other.isEnabled
        this.state = other.state
        this.threshold = other.threshold
        this.version = other.version
        this.numVotes = other.numVotes
        this.window = other.window
        this.voting = other.voting
    }

    override fun copy(): MoneroHardForkInfo {
        return MoneroHardForkInfo(this)
    }
}
