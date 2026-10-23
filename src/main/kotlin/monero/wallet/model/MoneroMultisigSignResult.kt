package monero.wallet.model

/**
 * Models the result of signing multisig transactions.
 */
class MoneroMultisigSignResult {
    var signedMultisigTxHex: String? = null
    var txHashes: List<String>? = null

    constructor()

    constructor(other: MoneroMultisigSignResult) {
        this.signedMultisigTxHex = other.signedMultisigTxHex
        this.txHashes = other.txHashes?.let { ArrayList(it) }
    }

    fun copy(): MoneroMultisigSignResult {
        return MoneroMultisigSignResult(this)
    }
}
