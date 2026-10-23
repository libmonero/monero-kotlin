package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Models a daemon peer.
 */
class MoneroPeer {
    var id: String? = null
    var address: String? = null
    var host: String? = null
    var port: Int? = null
    var isOnline: Boolean? = null
    var lastSeenTimestamp: Long? = null
    var pruningSeed: Int? = null
    var rpcPort: Int? = null
    var rpcCreditsPerHash: Long? = null
    var hash: String? = null
    var avgDownload: Long? = null
    var avgUpload: Long? = null
    var currentDownload: Long? = null
    var currentUpload: Long? = null
    var height: Long? = null
    var isIncoming: Boolean? = null
    var liveTime: Long? = null
    var isLocalIp: Boolean? = null
    var isLocalHost: Boolean? = null
    var numReceives: Long? = null
    var numSends: Long? = null
    var receiveIdleTime: Long? = null
    var sendIdleTime: Long? = null
    var state: String? = null
    var numSupportFlags: Int? = null
    // monero-cpp calls it addressType
    @JsonProperty("addressType")
    var connectionType: MoneroConnectionType? = null

    constructor()

    constructor(other: MoneroPeer) {
        this.id = other.id
        this.address = other.address
        this.host = other.host
        this.port = other.port
        this.isOnline = other.isOnline
        this.lastSeenTimestamp = other.lastSeenTimestamp
        this.pruningSeed = other.pruningSeed
        this.rpcPort = other.rpcPort
        this.rpcCreditsPerHash = other.rpcCreditsPerHash
        this.hash = other.hash
        this.avgDownload = other.avgDownload
        this.avgUpload = other.avgUpload
        this.currentDownload = other.currentDownload
        this.currentUpload = other.currentUpload
        this.height = other.height
        this.isIncoming = other.isIncoming
        this.liveTime = other.liveTime
        this.isLocalIp = other.isLocalIp
        this.isLocalHost = other.isLocalHost
        this.numReceives = other.numReceives
        this.numSends = other.numSends
        this.receiveIdleTime = other.receiveIdleTime
        this.sendIdleTime = other.sendIdleTime
        this.state = other.state
        this.numSupportFlags = other.numSupportFlags
        this.connectionType = other.connectionType
    }

    fun copy(): MoneroPeer {
        return MoneroPeer(this)
    }
}
