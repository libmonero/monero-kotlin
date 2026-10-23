package monero.daemon

import monero.daemon.model.MoneroBan
import monero.daemon.model.MoneroKeyImageSpentStatus
import monero.daemon.model.MoneroTx

/**
 * Abstract default implementation of a Monero daemon.
 */
abstract class MoneroDaemonDefault : MoneroDaemon {

    override fun getTx(txHash: String, prune: Boolean): MoneroTx? {
        return getTxs(listOf(txHash), prune).firstOrNull()
    }

    override fun getTxHex(txHash: String, prune: Boolean): String {
        return getTxHexes(listOf(txHash), prune)[0]
    }

    override fun relayTxByHash(txHash: String) {
        relayTxsByHash(listOf(txHash))
    }

    override fun flushTxPool(hash: String) {
        flushTxPool(listOf(hash))
    }

    override fun getKeyImageSpentStatus(keyImage: String): MoneroKeyImageSpentStatus {
        return getKeyImageSpentStatuses(listOf(keyImage))[0]
    }

    override fun setPeerBan(ban: MoneroBan) {
        setPeerBans(listOf(ban))
    }

    override fun submitBlock(blockBlob: String) {
        submitBlocks(listOf(blockBlob))
    }

    override fun removeBootstrapDaemon() {
        setBootstrapDaemon("")
    }
}
