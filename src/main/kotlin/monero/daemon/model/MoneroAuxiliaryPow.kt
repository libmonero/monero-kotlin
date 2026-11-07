package monero.daemon.model

/**
 * Models an auxiliary proof of work, which is merge mined with the blocks of Monero.
 */
class MoneroAuxiliaryPow {
    var id: String? = null
    var hash: String? = null

    constructor()

    /**
     * Construct the proof of work of an auxiliary chain.
     *
     * @param id is the id of the chain
     * @param hash is the hash of the block of the chain
     */
    constructor(id: String?, hash: String?) {
        this.id = id
        this.hash = hash
    }

    constructor(other: MoneroAuxiliaryPow) {
        this.id = other.id
        this.hash = other.hash
    }

    fun copy(): MoneroAuxiliaryPow {
        return MoneroAuxiliaryPow(this)
    }
}
