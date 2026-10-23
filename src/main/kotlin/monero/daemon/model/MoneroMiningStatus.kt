package monero.daemon.model

/**
 * Models daemon mining status.
 */
class MoneroMiningStatus {
    var isActive: Boolean? = null
    var isBackground: Boolean? = null
    var address: String? = null
    var speed: Long? = null
    var numThreads: Int? = null

    constructor()

    constructor(other: MoneroMiningStatus) {
        this.isActive = other.isActive
        this.isBackground = other.isBackground
        this.address = other.address
        this.speed = other.speed
        this.numThreads = other.numThreads
    }

    fun copy(): MoneroMiningStatus {
        return MoneroMiningStatus(this)
    }
}
