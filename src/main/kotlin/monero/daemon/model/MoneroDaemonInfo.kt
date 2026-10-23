package monero.daemon.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import monero.common.MoneroUInt128

/**
 * Models general information about daemon and the network.
 */
class MoneroDaemonInfo : MoneroRpcPaymentInfo {
    var version: String? = null
    var numAltBlocks: Long? = null
    var blockSizeLimit: Long? = null
    var blockSizeMedian: Long? = null
    var blockWeightLimit: Long? = null
    var blockWeightMedian: Long? = null
    // bootstrap node address used for immediate wallet usability while syncing
    var bootstrapDaemonAddress: String? = null
    var difficultyLow: BigInteger? = null
    var difficultyHigh: BigInteger? = null

    /**
     * Get or set the network difficulty, which has 128 bits: it is joined from its two halves, and setting it sets both.
     */
    @get:JsonIgnore
    var difficulty: BigInteger?
        get() = MoneroUInt128.combine(difficultyLow, difficultyHigh)
        set(value) {
            difficultyLow = MoneroUInt128.low(value)
            difficultyHigh = MoneroUInt128.high(value)
        }
    var cumulativeDifficultyLow: BigInteger? = null
    var cumulativeDifficultyHigh: BigInteger? = null

    /**
     * Get or set the cumulative difficulty, which has 128 bits: it is joined from its two halves, and setting it sets both.
     */
    @get:JsonIgnore
    var cumulativeDifficulty: BigInteger?
        get() = MoneroUInt128.combine(cumulativeDifficultyLow, cumulativeDifficultyHigh)
        set(value) {
            cumulativeDifficultyLow = MoneroUInt128.low(value)
            cumulativeDifficultyHigh = MoneroUInt128.high(value)
        }
    var freeSpace: Long? = null
    var numOfflinePeers: Int? = null
    var numOnlinePeers: Int? = null
    var height: Long? = null
    var heightWithoutBootstrap: Long? = null
    var networkType: MoneroNetworkType? = null
    var isOffline: Boolean? = null
    var numIncomingConnections: Int? = null
    var numOutgoingConnections: Int? = null
    var numRpcConnections: Int? = null
    var startTimestamp: Long? = null
    var adjustedTimestamp: Long? = null
    var target: Long? = null
    var targetHeight: Long? = null
    var numTxs: Int? = null
    var numTxsPool: Int? = null
    var wasBootstrapEverUsed: Boolean? = null
    var databaseSize: Long? = null
    var updateAvailable: Boolean? = null
    var isBusySyncing: Boolean? = null
    var isSynchronized: Boolean? = null
    var isRestricted: Boolean? = null
    var isRegtest: Boolean? = null

    constructor() : super()

    constructor(other: MoneroDaemonInfo) : super(other) {
        this.version = other.version
        this.numAltBlocks = other.numAltBlocks
        this.blockSizeLimit = other.blockSizeLimit
        this.blockSizeMedian = other.blockSizeMedian
        this.blockWeightLimit = other.blockWeightLimit
        this.blockWeightMedian = other.blockWeightMedian
        this.bootstrapDaemonAddress = other.bootstrapDaemonAddress
        this.difficultyLow = other.difficultyLow
        this.difficultyHigh = other.difficultyHigh
        this.cumulativeDifficultyLow = other.cumulativeDifficultyLow
        this.cumulativeDifficultyHigh = other.cumulativeDifficultyHigh
        this.freeSpace = other.freeSpace
        this.numOfflinePeers = other.numOfflinePeers
        this.numOnlinePeers = other.numOnlinePeers
        this.height = other.height
        this.heightWithoutBootstrap = other.heightWithoutBootstrap
        this.networkType = other.networkType
        this.isOffline = other.isOffline
        this.numIncomingConnections = other.numIncomingConnections
        this.numOutgoingConnections = other.numOutgoingConnections
        this.numRpcConnections = other.numRpcConnections
        this.startTimestamp = other.startTimestamp
        this.adjustedTimestamp = other.adjustedTimestamp
        this.target = other.target
        this.targetHeight = other.targetHeight
        this.numTxs = other.numTxs
        this.numTxsPool = other.numTxsPool
        this.wasBootstrapEverUsed = other.wasBootstrapEverUsed
        this.databaseSize = other.databaseSize
        this.updateAvailable = other.updateAvailable
        this.isBusySyncing = other.isBusySyncing
        this.isSynchronized = other.isSynchronized
        this.isRestricted = other.isRestricted
        this.isRegtest = other.isRegtest
    }

    override fun copy(): MoneroDaemonInfo {
        return MoneroDaemonInfo(this)
    }
}
