package monero.daemon.model

/**
 * Monero banhammer.
 */
class MoneroBan {
    // e.g. 192.168.1.100
    var host: String? = null
    // integer formatted IP
    var ip: Long? = null
    var isBanned: Boolean? = null
    var seconds: Long? = null

    constructor()

    constructor(other: MoneroBan) {
        this.host = other.host
        this.ip = other.ip
        this.isBanned = other.isBanned
        this.seconds = other.seconds
    }

    fun copy(): MoneroBan {
        return MoneroBan(this)
    }
}
