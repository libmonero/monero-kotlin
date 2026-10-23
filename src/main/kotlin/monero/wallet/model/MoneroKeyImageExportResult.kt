package monero.wallet.model

import monero.daemon.model.MoneroKeyImage

/**
 * Models the result of exporting the key images of a wallet.
 */
class MoneroKeyImageExportResult {
    // index of the first key image among the outputs of the wallet
    var offset: Long? = null
    var keyImages: List<MoneroKeyImage>? = null

    constructor()

    constructor(other: MoneroKeyImageExportResult) {
        this.offset = other.offset
        this.keyImages = other.keyImages?.map { it.copy() }
    }

    fun copy(): MoneroKeyImageExportResult {
        return MoneroKeyImageExportResult(this)
    }
}
