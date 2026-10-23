package monero.daemon.model

/**
 * Models a Monero key image.
 */
class MoneroKeyImage {
    var hex: String? = null
    var signature: String? = null

    constructor()

    constructor(other: MoneroKeyImage) {
        this.hex = other.hex
        this.signature = other.signature
    }

    fun copy(): MoneroKeyImage {
        return MoneroKeyImage(this)
    }
}
