package monero.daemon.model

/**
 * Models an auxiliary proof of work, which is merge mined with the blocks of Monero.
 */
class MoneroAuxiliaryPow {
    var id: String? = null
    var hash: String? = null

    constructor()

    constructor(other: MoneroAuxiliaryPow) {
        this.id = other.id
        this.hash = other.hash
    }

    fun copy(): MoneroAuxiliaryPow {
        return MoneroAuxiliaryPow(this)
    }
}
