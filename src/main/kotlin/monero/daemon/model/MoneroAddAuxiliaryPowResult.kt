package monero.daemon.model

/**
 * Models the result of adding auxiliary proofs of work to a block template.
 */
class MoneroAddAuxiliaryPowResult : MoneroRpcPaymentInfo {
    var blockTemplateBlob: String? = null
    var blockHashingBlob: String? = null
    var merkleRoot: String? = null
    var merkleTreeDepth: Int? = null
    var auxPow: List<MoneroAuxiliaryPow>? = null

    constructor() : super()

    constructor(other: MoneroAddAuxiliaryPowResult) : super(other) {
        this.blockTemplateBlob = other.blockTemplateBlob
        this.blockHashingBlob = other.blockHashingBlob
        this.merkleRoot = other.merkleRoot
        this.merkleTreeDepth = other.merkleTreeDepth
        this.auxPow = other.auxPow?.map { it.copy() }
    }

    override fun copy(): MoneroAddAuxiliaryPowResult {
        return MoneroAddAuxiliaryPowResult(this)
    }
}
