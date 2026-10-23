package monero.common

/**
 * Models a connection to a daemon or to a wallet RPC server.
 */
open class MoneroRpcConnection {
    var uri: String? = null
    var username: String? = null
    var password: String? = null
    var proxyUri: String? = null
    var zmqUri: String? = null
    // priority of the connection compared to the others, where 1 is the highest, then 2, and so on, and 0 is the lowest
    var priority: Int? = null
    var timeoutMs: Long? = null
    var responseTime: Long? = null
    var sslVerify: Boolean? = null
    var isOnline: Boolean? = null
    var isAuthenticated: Boolean? = null

    constructor()

    constructor(other: MoneroRpcConnection) {
        this.uri = other.uri
        this.username = other.username
        this.password = other.password
        this.proxyUri = other.proxyUri
        this.zmqUri = other.zmqUri
        this.priority = other.priority
        this.timeoutMs = other.timeoutMs
        this.responseTime = other.responseTime
        this.sslVerify = other.sslVerify
        this.isOnline = other.isOnline
        this.isAuthenticated = other.isAuthenticated
    }

    constructor(uri: String?, username: String? = null, password: String? = null) {
        this.uri = uri
        this.username = username
        this.password = password
    }

    open fun copy(): MoneroRpcConnection {
        return MoneroRpcConnection(this)
    }
}
