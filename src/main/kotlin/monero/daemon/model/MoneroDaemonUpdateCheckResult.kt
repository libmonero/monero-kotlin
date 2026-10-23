package monero.daemon.model

/**
 * Models the result of checking for a daemon update.
 */
open class MoneroDaemonUpdateCheckResult {
    var isUpdateAvailable: Boolean? = null
    var version: String? = null
    // hash used to verify the download
    var hash: String? = null
    var autoUri: String? = null
    var userUri: String? = null

    constructor()

    constructor(other: MoneroDaemonUpdateCheckResult) {
        this.isUpdateAvailable = other.isUpdateAvailable
        this.version = other.version
        this.hash = other.hash
        this.autoUri = other.autoUri
        this.userUri = other.userUri
    }

    open fun copy(): MoneroDaemonUpdateCheckResult {
        return MoneroDaemonUpdateCheckResult(this)
    }
}
