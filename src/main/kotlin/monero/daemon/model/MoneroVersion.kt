package monero.daemon.model

/**
 * Defines a Monero version.
 */
class MoneroVersion {
    var number: Int? = null
    var isRelease: Boolean? = null

    constructor()

    constructor(other: MoneroVersion) {
        this.number = other.number
        this.isRelease = other.isRelease
    }

    fun copy(): MoneroVersion {
        return MoneroVersion(this)
    }
}
