package monero.daemon.model

import monero.internal.Reconcile

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

    /**
     * Merge another copy of the same key image into this one: a field that is set on one side is taken, and the same value on both
     * is kept.
     *
     * @param other is the key image to merge into this one
     * @throws MoneroError if the two have a different value in a field
     */
    fun merge(other: MoneroKeyImage) {
        if (this === other) return
        hex = Reconcile.value(hex, other.hex, "key image hex")
        signature = Reconcile.value(signature, other.signature, "key image signature")
    }
}
