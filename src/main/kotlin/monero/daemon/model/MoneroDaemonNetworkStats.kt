package monero.daemon.model

/**
 * Models the network statistics of the daemon since it started.
 */
class MoneroDaemonNetworkStats : MoneroRpcPaymentInfo {
    var startTime: Long? = null
    var totalPacketsIn: Long? = null
    var totalBytesIn: Long? = null
    var totalPacketsOut: Long? = null
    var totalBytesOut: Long? = null

    constructor() : super()

    constructor(other: MoneroDaemonNetworkStats) : super(other) {
        this.startTime = other.startTime
        this.totalPacketsIn = other.totalPacketsIn
        this.totalBytesIn = other.totalBytesIn
        this.totalPacketsOut = other.totalPacketsOut
        this.totalBytesOut = other.totalBytesOut
    }

    override fun copy(): MoneroDaemonNetworkStats {
        return MoneroDaemonNetworkStats(this)
    }
}
