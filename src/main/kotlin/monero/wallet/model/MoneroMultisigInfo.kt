package monero.wallet.model

/**
 * Models information about a multisig wallet. The threshold and the number of participants of a wallet that is not multisig are not meaningful: monero-cpp does not set them.
 */
class MoneroMultisigInfo {
    var isMultisig: Boolean? = null
    var isReady: Boolean? = null
    var threshold: Int? = null
    var numParticipants: Int? = null

    constructor()

    constructor(other: MoneroMultisigInfo) {
        this.isMultisig = other.isMultisig
        this.isReady = other.isReady
        this.threshold = other.threshold
        this.numParticipants = other.numParticipants
    }

    fun copy(): MoneroMultisigInfo {
        return MoneroMultisigInfo(this)
    }
}
