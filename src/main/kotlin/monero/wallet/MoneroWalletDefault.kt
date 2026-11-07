package monero.wallet

import java.math.BigInteger
import monero.common.MoneroError
import monero.common.MoneroRpcConnection
import monero.daemon.model.MoneroKeyImage
import monero.daemon.model.MoneroNetworkType
import monero.daemon.model.MoneroVersion
import monero.wallet.model.MoneroAccount
import monero.wallet.model.MoneroAccountTag
import monero.wallet.model.MoneroAddressBookEntry
import monero.wallet.model.MoneroCheckReserve
import monero.wallet.model.MoneroCheckTx
import monero.wallet.model.MoneroIntegratedAddress
import monero.wallet.model.MoneroKeyImageExportResult
import monero.wallet.model.MoneroKeyImageImportResult
import monero.wallet.model.MoneroMessageSignatureResult
import monero.wallet.model.MoneroMessageSignatureType
import monero.wallet.model.MoneroMultisigInfo
import monero.wallet.model.MoneroMultisigInitResult
import monero.wallet.model.MoneroMultisigSignResult
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroSubaddress
import monero.wallet.model.MoneroSyncResult
import monero.wallet.model.MoneroTransfer
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxPriority
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroTxSet
import monero.wallet.model.MoneroTxWallet
import monero.wallet.model.MoneroWalletListener

/**
 * Abstract default implementation of a Monero wallet: the functions that are the same for every one, written with the ones that
 * they call. What a wallet does not override throws a [MoneroError] that says that it is not supported, as the base class of
 * monero-python does, so a wallet implements what it can do and nothing else.
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

    // not supported until a wallet overrides them
    override fun addListener(listener: MoneroWalletListener): Unit = throw notSupported("addListener")
    override fun removeListener(listener: MoneroWalletListener): Unit = throw notSupported("removeListener")
    override fun getListeners(): List<MoneroWalletListener> = throw notSupported("getListeners")
    override fun removeListeners(): Unit = throw notSupported("removeListeners")
    override fun getVersion(): MoneroVersion = throw notSupported("getVersion")
    override fun getPath(): String = throw notSupported("getPath")
    override fun getNetworkType(): MoneroNetworkType = throw notSupported("getNetworkType")
    override fun isViewOnly(): Boolean = throw notSupported("isViewOnly")
    override fun isClosed(): Boolean = throw notSupported("isClosed")
    override fun getSeed(): String = throw notSupported("getSeed")
    override fun getSeedLanguage(): String = throw notSupported("getSeedLanguage")
    override fun getPrivateViewKey(): String = throw notSupported("getPrivateViewKey")
    override fun getPrivateSpendKey(): String = throw notSupported("getPrivateSpendKey")
    override fun getPublicViewKey(): String = throw notSupported("getPublicViewKey")
    override fun getPublicSpendKey(): String = throw notSupported("getPublicSpendKey")
    override fun getPrimaryAddress(): String = throw notSupported("getPrimaryAddress")
    override fun getAddress(accountIdx: Int, subaddressIdx: Int): String = throw notSupported("getAddress")
    override fun getAddressIndex(address: String): MoneroSubaddress = throw notSupported("getAddressIndex")
    override fun getIntegratedAddress(standardAddress: String?, paymentId: String?): MoneroIntegratedAddress = throw notSupported("getIntegratedAddress")
    override fun decodeIntegratedAddress(integratedAddress: String): MoneroIntegratedAddress = throw notSupported("decodeIntegratedAddress")
    override fun getAttribute(key: String): String? = throw notSupported("getAttribute")
    override fun setAttribute(key: String, value: String): Unit = throw notSupported("setAttribute")
    override fun getDefaultFeePriority(): MoneroTxPriority = throw notSupported("getDefaultFeePriority")
    override fun save(): Unit = throw notSupported("save")
    override fun moveTo(path: String, password: String): Unit = throw notSupported("moveTo")
    override fun changePassword(oldPassword: String, newPassword: String): Unit = throw notSupported("changePassword")
    override fun close(save: Boolean): Unit = throw notSupported("close")
    override fun getDaemonConnection(): MoneroRpcConnection? = throw notSupported("getDaemonConnection")
    override fun setDaemonConnection(connection: MoneroRpcConnection?, isTrusted: Boolean?): Unit = throw notSupported("setDaemonConnection")
    override fun isConnectedToDaemon(): Boolean = throw notSupported("isConnectedToDaemon")
    override fun isDaemonSynced(): Boolean = throw notSupported("isDaemonSynced")
    override fun isDaemonTrusted(): Boolean = throw notSupported("isDaemonTrusted")
    override fun isSynced(): Boolean = throw notSupported("isSynced")
    override fun getHeight(): Long = throw notSupported("getHeight")
    override fun getDaemonHeight(): Long = throw notSupported("getDaemonHeight")
    override fun getDaemonMaxPeerHeight(): Long = throw notSupported("getDaemonMaxPeerHeight")
    override fun getHeightByDate(year: Int, month: Int, day: Int): Long = throw notSupported("getHeightByDate")
    override fun getRestoreHeight(): Long = throw notSupported("getRestoreHeight")
    override fun setRestoreHeight(restoreHeight: Long): Unit = throw notSupported("setRestoreHeight")
    override fun sync(startHeight: Long?, listener: MoneroWalletListener?): MoneroSyncResult = throw notSupported("sync")
    override fun startSyncing(syncPeriodInMs: Long): Unit = throw notSupported("startSyncing")
    override fun stopSyncing(): Unit = throw notSupported("stopSyncing")
    override fun waitForNextBlock(): Long = throw notSupported("waitForNextBlock")
    override fun scanTxs(txHashes: List<String>): Unit = throw notSupported("scanTxs")
    override fun rescanSpent(): Unit = throw notSupported("rescanSpent")
    override fun rescanBlockchain(): Unit = throw notSupported("rescanBlockchain")
    override fun startMining(numThreads: Long?, backgroundMining: Boolean?, ignoreBattery: Boolean?): Unit = throw notSupported("startMining")
    override fun stopMining(): Unit = throw notSupported("stopMining")
    override fun getBalance(accountIdx: Int?, subaddressIdx: Int?): BigInteger = throw notSupported("getBalance")
    override fun getUnlockedBalance(accountIdx: Int?, subaddressIdx: Int?): BigInteger = throw notSupported("getUnlockedBalance")
    override fun getAccounts(includeSubaddresses: Boolean, tag: String?): List<MoneroAccount> = throw notSupported("getAccounts")
    override fun getAccount(accountIdx: Int, includeSubaddresses: Boolean): MoneroAccount = throw notSupported("getAccount")
    override fun createAccount(label: String?): MoneroAccount = throw notSupported("createAccount")
    override fun getAccountTags(): List<MoneroAccountTag> = throw notSupported("getAccountTags")
    override fun tagAccounts(tag: String, accountIndices: List<Int>): Unit = throw notSupported("tagAccounts")
    override fun untagAccounts(accountIndices: List<Int>): Unit = throw notSupported("untagAccounts")
    override fun setAccountTagLabel(tag: String, label: String): Unit = throw notSupported("setAccountTagLabel")
    override fun getSubaddresses(accountIdx: Int, subaddressIndices: List<Int>?): List<MoneroSubaddress> = throw notSupported("getSubaddresses")
    override fun createSubaddress(accountIdx: Int, label: String?): MoneroSubaddress = throw notSupported("createSubaddress")
    override fun setSubaddressLabel(accountIdx: Int, subaddressIdx: Int, label: String?): Unit = throw notSupported("setSubaddressLabel")
    override fun getTxs(query: MoneroTxQuery?): List<MoneroTxWallet> = throw notSupported("getTxs")
    override fun getTransfers(query: MoneroTransferQuery?): List<MoneroTransfer> = throw notSupported("getTransfers")
    override fun getOutputs(query: MoneroOutputQuery?): List<MoneroOutputWallet> = throw notSupported("getOutputs")
    override fun exportOutputs(all: Boolean): String = throw notSupported("exportOutputs")
    override fun importOutputs(outputsHex: String): Int = throw notSupported("importOutputs")
    override fun exportKeyImages(all: Boolean): MoneroKeyImageExportResult = throw notSupported("exportKeyImages")
    override fun importKeyImages(keyImages: List<MoneroKeyImage>, offset: Long): MoneroKeyImageImportResult = throw notSupported("importKeyImages")
    override fun freezeOutput(keyImage: String): Unit = throw notSupported("freezeOutput")
    override fun thawOutput(keyImage: String): Unit = throw notSupported("thawOutput")
    override fun isOutputFrozen(keyImage: String): Boolean = throw notSupported("isOutputFrozen")
    override fun createTx(config: MoneroTxConfig): MoneroTxWallet = throw notSupported("createTx")
    override fun createTxs(config: MoneroTxConfig): List<MoneroTxWallet> = throw notSupported("createTxs")
    override fun sweepOutput(config: MoneroTxConfig): MoneroTxWallet = throw notSupported("sweepOutput")
    override fun sweepUnlocked(config: MoneroTxConfig): List<MoneroTxWallet> = throw notSupported("sweepUnlocked")
    override fun sweepDust(relay: Boolean): List<MoneroTxWallet> = throw notSupported("sweepDust")
    override fun relayTx(txMetadata: String): String = throw notSupported("relayTx")
    override fun relayTx(tx: MoneroTxWallet): String = throw notSupported("relayTx")
    override fun relayTxs(txMetadatas: List<String>): List<String> = throw notSupported("relayTxs")
    override fun relayTxs(txs: Collection<MoneroTxWallet>): List<String> = throw notSupported("relayTxs")
    override fun describeTxSet(txSet: MoneroTxSet): MoneroTxSet = throw notSupported("describeTxSet")
    override fun signTxs(unsignedTxHex: String): MoneroTxSet = throw notSupported("signTxs")
    override fun submitTxs(signedTxHex: String): List<String> = throw notSupported("submitTxs")
    override fun getTxNote(txHash: String): String = throw notSupported("getTxNote")
    override fun getTxNotes(txHashes: List<String>): List<String> = throw notSupported("getTxNotes")
    override fun setTxNote(txHash: String, note: String): Unit = throw notSupported("setTxNote")
    override fun setTxNotes(txHashes: List<String>, notes: List<String>): Unit = throw notSupported("setTxNotes")
    override fun getAddressBookEntries(indices: List<Long>?): List<MoneroAddressBookEntry> = throw notSupported("getAddressBookEntries")
    override fun addAddressBookEntry(address: String, description: String?): Long = throw notSupported("addAddressBookEntry")
    override fun editAddressBookEntry(index: Long, address: String?, description: String?): Unit = throw notSupported("editAddressBookEntry")
    override fun deleteAddressBookEntry(index: Long): Unit = throw notSupported("deleteAddressBookEntry")
    override fun getPaymentUri(config: MoneroTxConfig): String = throw notSupported("getPaymentUri")
    override fun parsePaymentUri(uri: String): MoneroTxConfig = throw notSupported("parsePaymentUri")
    override fun signMessage(message: String, signatureType: MoneroMessageSignatureType, accountIdx: Int, subaddressIdx: Int): String = throw notSupported("signMessage")
    override fun verifyMessage(message: String, address: String, signature: String): MoneroMessageSignatureResult = throw notSupported("verifyMessage")
    override fun getTxKey(txHash: String): String = throw notSupported("getTxKey")
    override fun checkTxKey(txHash: String, txKey: String, address: String): MoneroCheckTx = throw notSupported("checkTxKey")
    override fun getTxProof(txHash: String, address: String, message: String?): String = throw notSupported("getTxProof")
    override fun checkTxProof(txHash: String, address: String, message: String?, signature: String): MoneroCheckTx = throw notSupported("checkTxProof")
    override fun getSpendProof(txHash: String, message: String?): String = throw notSupported("getSpendProof")
    override fun checkSpendProof(txHash: String, message: String?, signature: String): Boolean = throw notSupported("checkSpendProof")
    override fun getReserveProofWallet(message: String?): String = throw notSupported("getReserveProofWallet")
    override fun getReserveProofAccount(accountIdx: Int, amount: BigInteger, message: String?): String = throw notSupported("getReserveProofAccount")
    override fun checkReserveProof(address: String, message: String?, signature: String): MoneroCheckReserve = throw notSupported("checkReserveProof")
    override fun isMultisig(): Boolean = throw notSupported("isMultisig")
    override fun getMultisigInfo(): MoneroMultisigInfo = throw notSupported("getMultisigInfo")
    override fun isMultisigImportNeeded(): Boolean = throw notSupported("isMultisigImportNeeded")
    override fun prepareMultisig(): String = throw notSupported("prepareMultisig")
    override fun makeMultisig(multisigHexes: List<String>, threshold: Int, password: String): String = throw notSupported("makeMultisig")
    override fun exchangeMultisigKeys(multisigHexes: List<String>, password: String): MoneroMultisigInitResult = throw notSupported("exchangeMultisigKeys")
    override fun exportMultisigHex(): String = throw notSupported("exportMultisigHex")
    override fun importMultisigHex(multisigHexes: List<String>, refreshAfterImport: Boolean): Int = throw notSupported("importMultisigHex")
    override fun signMultisigTxHex(multisigTxHex: String): MoneroMultisigSignResult = throw notSupported("signMultisigTxHex")
    override fun submitMultisigTxHex(signedMultisigTxHex: String): List<String> = throw notSupported("submitMultisigTxHex")

    private fun notSupported(method: String) = MoneroError("$method is not supported")
}
