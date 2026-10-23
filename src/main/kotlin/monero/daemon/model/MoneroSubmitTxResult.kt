package monero.daemon.model

/**
 * Models the result from submitting a tx to a daemon.
 */
class MoneroSubmitTxResult : MoneroRpcPaymentInfo {
    var isGood: Boolean? = null
    var isRelayed: Boolean? = null
    var isDoubleSpend: Boolean? = null
    var isFeeTooLow: Boolean? = null
    var isMixinTooLow: Boolean? = null
    var hasInvalidInput: Boolean? = null
    var hasInvalidOutput: Boolean? = null
    var hasTooFewOutputs: Boolean? = null
    var isOverspend: Boolean? = null
    var isTooBig: Boolean? = null
    var sanityCheckFailed: Boolean? = null
    var reason: String? = null
    var isTxExtraTooBig: Boolean? = null
    var isNonZeroUnlockTime: Boolean? = null

    constructor() : super()

    constructor(other: MoneroSubmitTxResult) : super(other) {
        this.isGood = other.isGood
        this.isRelayed = other.isRelayed
        this.isDoubleSpend = other.isDoubleSpend
        this.isFeeTooLow = other.isFeeTooLow
        this.isMixinTooLow = other.isMixinTooLow
        this.hasInvalidInput = other.hasInvalidInput
        this.hasInvalidOutput = other.hasInvalidOutput
        this.hasTooFewOutputs = other.hasTooFewOutputs
        this.isOverspend = other.isOverspend
        this.isTooBig = other.isTooBig
        this.sanityCheckFailed = other.sanityCheckFailed
        this.reason = other.reason
        this.isTxExtraTooBig = other.isTxExtraTooBig
        this.isNonZeroUnlockTime = other.isNonZeroUnlockTime
    }

    override fun copy(): MoneroSubmitTxResult {
        return MoneroSubmitTxResult(this)
    }
}
