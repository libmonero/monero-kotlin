package monero.wallet

import monero.common.MoneroError
import monero.common.MoneroRpcConnection
import monero.wallet.model.MoneroSubaddress
import monero.wallet.model.MoneroTransfer
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroTxSet
import monero.wallet.model.MoneroTxWallet
import monero.wallet.model.MoneroWalletListener
import monero.wallet.model.MoneroSyncResult

/**
 * Abstract default implementation of a Monero wallet.
 */
abstract class MoneroWalletDefault : MoneroWallet {

    override fun setDaemonConnection(uri: String, username: String?, password: String?, proxyUri: String?, isTrusted: Boolean?) {
        val connection = MoneroRpcConnection(uri, username, password)
        connection.proxyUri = proxyUri
        setDaemonConnection(connection, isTrusted)
    }

    override fun sync(listener: MoneroWalletListener): MoneroSyncResult {
        return sync(null, listener)
    }

    override fun getSubaddress(accountIdx: Int, subaddressIdx: Int): MoneroSubaddress {
        val subaddresses = getSubaddresses(accountIdx, listOf(subaddressIdx))
        if (subaddresses.size != 1) throw MoneroError("The subaddress $accountIdx,$subaddressIdx is not found")
        return subaddresses[0]
    }

    // the txs of a query by hashes come in the order of the blocks: sort them by the hashes that were asked for
    override fun getTxs(txHashes: List<String>): List<MoneroTxWallet> {
        val query = MoneroTxQuery()
        query.hashes = ArrayList(txHashes)
        val position = HashMap<String, Int>()
        txHashes.forEachIndexed { i, hash -> position.putIfAbsent(hash, i) }
        return getTxs(query).sortedBy { tx -> tx.hash?.let { position[it] } ?: Int.MAX_VALUE }
    }

    override fun getTx(txHash: String): MoneroTxWallet? {
        return getTxs(listOf(txHash)).firstOrNull()
    }

    override fun getTransfers(accountIdx: Int, subaddressIdx: Int?): List<MoneroTransfer> {
        val query = MoneroTransferQuery()
        query.accountIndex = accountIdx
        query.subaddressIndex = subaddressIdx
        return getTransfers(query)
    }

    override fun describeUnsignedTxSet(unsignedTxHex: String): MoneroTxSet {
        val txSet = MoneroTxSet()
        txSet.unsignedTxHex = unsignedTxHex
        return describeTxSet(txSet)
    }

    override fun describeMultisigTxSet(multisigTxHex: String): MoneroTxSet {
        val txSet = MoneroTxSet()
        txSet.multisigTxHex = multisigTxHex
        return describeTxSet(txSet)
    }
}
