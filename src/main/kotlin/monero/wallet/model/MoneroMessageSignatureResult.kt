package monero.wallet.model

/**
 * Models the result of verifying the signature of a message.
 */
class MoneroMessageSignatureResult {
    var isGood: Boolean? = null
    var isOld: Boolean? = null
    var version: Int? = null
    var signatureType: MoneroMessageSignatureType? = null

    constructor()

    constructor(other: MoneroMessageSignatureResult) {
        this.isGood = other.isGood
        this.isOld = other.isOld
        this.version = other.version
        this.signatureType = other.signatureType
    }

    fun copy(): MoneroMessageSignatureResult {
        return MoneroMessageSignatureResult(this)
    }
}
