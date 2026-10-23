package monero.wallet.model

import monero.common.MoneroRpcConnection
import monero.daemon.model.MoneroNetworkType

/**
 * Configures a wallet to create: it is restored from its seed if the config has one, from its keys if it has the address or the private keys, and it has a new random seed otherwise.
 */
class MoneroWalletConfig {
    // path of the wallet file, which is written with its keys file; the wallet stays in memory without it
    var path: String? = null
    var password: String? = null
    var networkType: MoneroNetworkType? = null
    // daemon that the wallet connects to when it is created
    var server: MoneroRpcConnection? = null
    // when it is not set, a daemon on a local address is trusted and the others are not
    var isTrustedDaemon: Boolean? = null
    // mnemonic seed to restore the wallet from; for a multisig wallet, its multisig data in hexadecimal format
    var seed: String? = null
    var seedOffset: String? = null
    var primaryAddress: String? = null
    var privateViewKey: String? = null
    // without it, the wallet is view-only
    var privateSpendKey: String? = null
    var restoreHeight: Long? = null
    var language: String? = null
    var saveCurrent: Boolean? = null
    var accountLookahead: Long? = null
    var subaddressLookahead: Long? = null
    var isMultisig: Boolean? = null
    // the way that monero-cpp has for a regtest wallet, which is a flag of the main network
    var regtest: Boolean? = null

    constructor()

    constructor(other: MoneroWalletConfig) {
        this.path = other.path
        this.password = other.password
        this.networkType = other.networkType
        this.server = other.server?.copy()
        this.isTrustedDaemon = other.isTrustedDaemon
        this.seed = other.seed
        this.seedOffset = other.seedOffset
        this.primaryAddress = other.primaryAddress
        this.privateViewKey = other.privateViewKey
        this.privateSpendKey = other.privateSpendKey
        this.restoreHeight = other.restoreHeight
        this.language = other.language
        this.saveCurrent = other.saveCurrent
        this.accountLookahead = other.accountLookahead
        this.subaddressLookahead = other.subaddressLookahead
        this.isMultisig = other.isMultisig
        this.regtest = other.regtest
    }

    fun copy(): MoneroWalletConfig {
        return MoneroWalletConfig(this)
    }
}
