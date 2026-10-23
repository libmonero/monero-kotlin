package monero.wallet.model

/**
 * Models the result of a step of the creation of a multisig wallet: the address of the wallet when the participants are done, and the multisig hex to share with them for the next step.
 */
class MoneroMultisigInitResult {
    var address: String? = null
    var multisigHex: String? = null

    constructor()

    constructor(other: MoneroMultisigInitResult) {
        this.address = other.address
        this.multisigHex = other.multisigHex
    }

    fun copy(): MoneroMultisigInitResult {
        return MoneroMultisigInitResult(this)
    }
}
