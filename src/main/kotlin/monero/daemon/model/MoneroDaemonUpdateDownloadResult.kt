package monero.daemon.model

/**
 * Models the result of downloading an update.
 */
class MoneroDaemonUpdateDownloadResult : MoneroDaemonUpdateCheckResult {
    var downloadPath: String? = null

    constructor() : super()

    constructor(other: MoneroDaemonUpdateDownloadResult) : super(other) {
        this.downloadPath = other.downloadPath
    }

    override fun copy(): MoneroDaemonUpdateDownloadResult {
        return MoneroDaemonUpdateDownloadResult(this)
    }
}
