package monero.wallet.lws.wire

import com.fasterxml.jackson.annotation.JsonProperty

// response of /get_version, the 11 fields the real server sends.
// network_type stays a string bc lws writes "main" for mainnet, which
// is not a name that MoneroNetworkType parses
class MoneroLwsVersion {
    @JsonProperty("server_type") var serverType: String? = null
    @JsonProperty("server_version") var serverVersion: String? = null
    @JsonProperty("last_git_commit_hash") var lastGitCommitHash: String? = null
    @JsonProperty("last_git_commit_date") var lastGitCommitDate: String? = null
    @JsonProperty("git_branch_name") var gitBranchName: String? = null
    @JsonProperty("monero_version_full") var moneroVersionFull: String? = null
    @JsonProperty("blockchain_height") var blockchainHeight: Long? = null
    var api: Long? = null
    @JsonProperty("max_subaddresses") var maxSubaddresses: Long? = null
    @JsonProperty("network_type") var networkType: String? = null
    @JsonProperty("testnet") var isTestnet: Boolean? = null

    constructor()

    constructor(other: MoneroLwsVersion) {
        this.serverType = other.serverType
        this.serverVersion = other.serverVersion
        this.lastGitCommitHash = other.lastGitCommitHash
        this.lastGitCommitDate = other.lastGitCommitDate
        this.gitBranchName = other.gitBranchName
        this.moneroVersionFull = other.moneroVersionFull
        this.blockchainHeight = other.blockchainHeight
        this.api = other.api
        this.maxSubaddresses = other.maxSubaddresses
        this.networkType = other.networkType
        this.isTestnet = other.isTestnet
    }

    fun copy(): MoneroLwsVersion {
        return MoneroLwsVersion(this)
    }
}
