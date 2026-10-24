package monero.wallet

import java.math.BigInteger
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
 * Monero wallet interface.
 */
interface MoneroWallet {

    /**
     * Register a listener to receive wallet notifications.
     *
     * @param listener the listener to register
     */
    fun addListener(listener: MoneroWalletListener)

    /**
     * Unregister a listener to receive wallet notifications.
     *
     * @param listener a previously registered listener to be unregistered
     */
    fun removeListener(listener: MoneroWalletListener)

    /**
     * Get the listeners registered with the wallet.
     *
     * @return the registered listeners
     */
    fun getListeners(): List<MoneroWalletListener>

    /**
     * Unregister all listeners registered with the wallet.
     */
    fun removeListeners()

    /**
     * Get the version of the wallet.
     *
     * @return the version of the wallet
     */
    fun getVersion(): MoneroVersion

    /**
     * Get the wallet's path.
     *
     * @return the path the wallet can be opened with
     */
    fun getPath(): String

    /**
     * Get the wallet's network type.
     *
     * @return the network type of the wallet
     */
    fun getNetworkType(): MoneroNetworkType

    /**
     * Indicates if the wallet is view-only, meaning it does not have the private spend key and can therefore only
     * observe incoming outputs.
     *
     * @return true if the wallet is view-only, false otherwise
     */
    fun isViewOnly(): Boolean

    /**
     * Indicates if the wallet is closed or not.
     *
     * @return true if the wallet is closed, false otherwise
     */
    fun isClosed(): Boolean

    /**
     * Get the wallet's mnemonic phrase or seed.
     *
     * @return the wallet's mnemonic phrase or seed
     */
    fun getSeed(): String

    /**
     * Get the language of the wallet's mnemonic phrase or seed.
     *
     * @return the language of the wallet's mnemonic phrase or seed
     */
    fun getSeedLanguage(): String

    /**
     * Get the wallet's private view key.
     *
     * @return the wallet's private view key
     */
    fun getPrivateViewKey(): String

    /**
     * Get the wallet's private spend key.
     *
     * @return the wallet's private spend key
     */
    fun getPrivateSpendKey(): String

    /**
     * Get the wallet's public view key.
     *
     * @return the wallet's public view key
     */
    fun getPublicViewKey(): String

    /**
     * Get the wallet's public spend key.
     *
     * @return the wallet's public spend key
     */
    fun getPublicSpendKey(): String

    /**
     * Get the wallet's primary address.
     *
     * @return the wallet's primary address
     */
    fun getPrimaryAddress(): String

    /**
     * Get the address of a specific subaddress.
     *
     * @param accountIdx specifies the account index of the address's subaddress
     * @param subaddressIdx specifies the subaddress index within the account
     * @return the receive address of the specified subaddress
     */
    fun getAddress(accountIdx: Int, subaddressIdx: Int): String

    /**
     * Get the account and subaddress index of the given address.
     *
     * @param address is the address to get the account and subaddress index from
     * @return the account and subaddress indices
     */
    fun getAddressIndex(address: String): MoneroSubaddress

    /**
     * Get an integrated address based on the given standard address and payment ID.
     *
     * @param standardAddress is the standard address to generate the integrated address from, or null for the wallet's
     *      primary address
     * @param paymentId is the payment ID to generate an integrated address from, or null for a random one
     * @return the integrated address
     */
    fun getIntegratedAddress(standardAddress: String? = null, paymentId: String? = null): MoneroIntegratedAddress

    /**
     * Decode an integrated address to get its standard address and payment id.
     *
     * @param integratedAddress is the integrated address to decode
     * @return the decoded integrated address, including the standard address and payment id
     */
    fun decodeIntegratedAddress(integratedAddress: String): MoneroIntegratedAddress

    /**
     * Get an attribute that the wallet stores with its files.
     *
     * @param key is the attribute to get the value of
     * @return the attribute's value, or null if the attribute does not exist
     */
    fun getAttribute(key: String): String?

    /**
     * Set an arbitrary attribute, stored with the wallet's files.
     *
     * @param key is the attribute key
     * @param value is the attribute value
     */
    fun setAttribute(key: String, value: String)

    /**
     * Get the default priority of the fee of the transactions.
     *
     * @return the default priority
     */
    fun getDefaultFeePriority(): MoneroTxPriority

    /**
     * Save the wallet at its current path.
     */
    fun save()

    /**
     * Move the wallet from its current path to the given path.
     *
     * @param path is the wallet's destination path
     * @param password is the password of the wallet files at the destination path
     */
    fun moveTo(path: String, password: String)

    /**
     * Change the wallet password.
     *
     * @param oldPassword is the wallet's old password
     * @param newPassword is the wallet's new password
     */
    fun changePassword(oldPassword: String, newPassword: String)

    /**
     * Close the wallet, saving its state first if requested.
     *
     * @param save specifies if the wallet's state should be saved before closing
     */
    fun close(save: Boolean)

    /**
     * Get the wallet's daemon connection.
     *
     * @return the wallet's daemon connection, or null if it has none
     */
    fun getDaemonConnection(): MoneroRpcConnection?

    /**
     * Set the wallet's daemon connection.
     *
     * @param connection manages connection configuration, or null to disconnect the wallet from its daemon
     * @param isTrusted indicates if the daemon is trusted, or null to trust it only if its address is local
     */
    fun setDaemonConnection(connection: MoneroRpcConnection?, isTrusted: Boolean? = null)

    /**
     * Set the wallet's daemon connection.
     *
     * @param uri is the uri of the daemon to connect to, or an empty string to disconnect the wallet from its daemon
     * @param username is the username to authenticate with, or null for none
     * @param password is the password to authenticate with, or null for none
     * @param proxyUri is the uri of a proxy to connect through, or null for none
     * @param isTrusted indicates if the daemon is trusted, or null to trust it only if its address is local
     */
    fun setDaemonConnection(uri: String, username: String? = null, password: String? = null, proxyUri: String? = null, isTrusted: Boolean? = null)

    /**
     * Indicates if the wallet is connected to its daemon.
     *
     * @return true if the wallet is connected to its daemon, false otherwise
     */
    fun isConnectedToDaemon(): Boolean

    /**
     * Indicates if the wallet's daemon is synced with the network.
     *
     * @return true if the daemon is synced with the network, false otherwise
     */
    fun isDaemonSynced(): Boolean

    /**
     * Indicates if the wallet's daemon is trusted.
     *
     * @return true if the daemon is trusted, false otherwise
     */
    fun isDaemonTrusted(): Boolean

    /**
     * Indicates if the wallet is synced with the daemon.
     *
     * @return true if the wallet is synced with the daemon, false otherwise
     */
    fun isSynced(): Boolean

    /**
     * Get the number of blocks the wallet has synced.
     *
     * @return the height of the last block the wallet has synced
     */
    fun getHeight(): Long

    /**
     * Get the blockchain's height.
     *
     * @return the blockchain's height
     */
    fun getDaemonHeight(): Long

    /**
     * Get the maximum height of the peers the wallet's daemon is connected to.
     *
     * @return the maximum height of the peers the wallet's daemon is connected to
     */
    fun getDaemonMaxPeerHeight(): Long

    /**
     * Get the blockchain's height by date as a conservative estimate for scanning.
     *
     * @param year year of the height to get
     * @param month month of the height to get as a number between 1 and 12
     * @param day day of the height to get as a number between 1 and 31
     * @return the blockchain's approximate height at the given date
     */
    fun getHeightByDate(year: Int, month: Int, day: Int): Long

    /**
     * Get the height of the first block that the wallet scans.
     *
     * @return the height of the first block that the wallet scans
     */
    fun getRestoreHeight(): Long

    /**
     * Set the height of the first block that the wallet scans.
     *
     * @param restoreHeight is the height of the first block that the wallet scans
     */
    fun setRestoreHeight(restoreHeight: Long)

    /**
     * Synchronize the wallet with the daemon as a one-time synchronous process.
     *
     * @param startHeight is the start height to sync from, or null to sync from the last synced block
     * @param listener receives notifications as the sync progresses, or null for none
     * @return the sync result
     */
    fun sync(startHeight: Long? = null, listener: MoneroWalletListener? = null): MoneroSyncResult

    /**
     * Synchronize the wallet with the daemon as a one-time synchronous process.
     *
     * @param listener receives notifications as the sync progresses
     * @return the sync result
     */
    fun sync(listener: MoneroWalletListener): MoneroSyncResult

    /**
     * Start background synchronizing with a maximum period between syncs.
     *
     * @param syncPeriodInMs is the maximum period between syncs in milliseconds
     */
    fun startSyncing(syncPeriodInMs: Long = 10000)

    /**
     * Stop synchronizing the wallet with the daemon.
     */
    fun stopSyncing()

    /**
     * Wait for the next block to be added to the chain. The wallet must be syncing in the background.
     *
     * @return the height of the next block that was added to the chain
     */
    fun waitForNextBlock(): Long

    /**
     * Scan transactions by their hash.
     *
     * @param txHashes are the hashes of the transactions to scan
     */
    fun scanTxs(txHashes: List<String>)

    /**
     * Rescan the blockchain for spent outputs. It needs a trusted daemon.
     */
    fun rescanSpent()

    /**
     * Rescan the blockchain from scratch, losing any information which cannot be recovered from the blockchain itself.
     */
    fun rescanBlockchain()

    /**
     * Start mining.
     *
     * @param numThreads is the number of threads created for mining, or null for the default
     * @param backgroundMining specifies if mining should occur in the background, or null for the default
     * @param ignoreBattery specifies if the battery should be ignored for mining, or null for the default
     */
    fun startMining(numThreads: Long? = null, backgroundMining: Boolean? = null, ignoreBattery: Boolean? = null)

    /**
     * Stop mining.
     */
    fun stopMining()

    /**
     * Get the balance of the wallet, an account or a subaddress.
     *
     * @param accountIdx is the index of the account, or null for the whole wallet
     * @param subaddressIdx is the index of the subaddress within the account, or null for the whole account. It needs
     *      an account
     * @return the balance, in atomic units
     */
    fun getBalance(accountIdx: Int? = null, subaddressIdx: Int? = null): BigInteger

    /**
     * Get the unlocked balance of the wallet, an account or a subaddress.
     *
     * @param accountIdx is the index of the account, or null for the whole wallet
     * @param subaddressIdx is the index of the subaddress within the account, or null for the whole account. It needs
     *      an account
     * @return the unlocked balance, in atomic units
     */
    fun getUnlockedBalance(accountIdx: Int? = null, subaddressIdx: Int? = null): BigInteger

    /**
     * Get the accounts of the wallet.
     *
     * @param includeSubaddresses includes the subaddresses of each account if true
     * @param tag is the tag of the accounts to get, or null for all of them
     * @return the accounts
     */
    fun getAccounts(includeSubaddresses: Boolean = false, tag: String? = null): List<MoneroAccount>

    /**
     * Get an account.
     *
     * @param accountIdx is the index of the account to get
     * @param includeSubaddresses includes the subaddresses of the account if true
     * @return the account
     */
    fun getAccount(accountIdx: Int, includeSubaddresses: Boolean = false): MoneroAccount

    /**
     * Create a new account.
     *
     * @param label is the label to apply to the new account, or null for none
     * @return the created account
     */
    fun createAccount(label: String? = null): MoneroAccount

    /**
     * Get the tags of the accounts.
     *
     * @return the tags of the accounts
     */
    fun getAccountTags(): List<MoneroAccountTag>

    /**
     * Tag accounts.
     *
     * @param tag is the tag to apply to the specified accounts
     * @param accountIndices are the indices of the accounts to tag
     */
    fun tagAccounts(tag: String, accountIndices: List<Int>)

    /**
     * Untag accounts.
     *
     * @param accountIndices are the indices of the accounts to untag
     */
    fun untagAccounts(accountIndices: List<Int>)

    /**
     * Set the label of an account tag.
     *
     * @param tag is the tag to set a label for
     * @param label is the label to set
     */
    fun setAccountTagLabel(tag: String, label: String)

    /**
     * Get the subaddresses in an account.
     *
     * @param accountIdx specifies the account to get subaddresses within
     * @param subaddressIndices are the indices of the subaddresses to get, or null for all of them
     * @return the subaddresses
     */
    fun getSubaddresses(accountIdx: Int, subaddressIndices: List<Int>? = null): List<MoneroSubaddress>

    /**
     * Get a subaddress.
     *
     * @param accountIdx specifies the index of the subaddress's account
     * @param subaddressIdx specifies the index of the subaddress within the account
     * @return the subaddress
     */
    fun getSubaddress(accountIdx: Int, subaddressIdx: Int): MoneroSubaddress

    /**
     * Create a subaddress within an account.
     *
     * @param accountIdx specifies the index of the account to create the subaddress within
     * @param label is the label for the subaddress, or null for none
     * @return the created subaddress
     */
    fun createSubaddress(accountIdx: Int, label: String? = null): MoneroSubaddress

    /**
     * Set the label of a subaddress.
     *
     * @param accountIdx specifies the index of the account with the subaddress
     * @param subaddressIdx specifies the index of the subaddress within the account
     * @param label is the label to set, or null to clear it
     */
    fun setSubaddressLabel(accountIdx: Int, subaddressIdx: Int, label: String? = null)

    /**
     * Get the transactions of the wallet: the ones that are in a block, in the pool, or that were relayed and failed.
     *
     * @param query filters the transactions, or null for all of them
     * @return the transactions that match the query
     */
    fun getTxs(query: MoneroTxQuery? = null): List<MoneroTxWallet>

    /**
     * Get wallet transactions by their hash, in the order of the hashes.
     *
     * @param txHashes are the hashes of the transactions to get
     * @return the transactions of the hashes that the wallet has
     */
    fun getTxs(txHashes: List<String>): List<MoneroTxWallet>

    /**
     * Get a wallet transaction by its hash.
     *
     * @param txHash is the hash of the transaction to get
     * @return the transaction, or null if the wallet doesn't have it
     */
    fun getTx(txHash: String): MoneroTxWallet?

    /**
     * Get incoming and outgoing transfers to and from this wallet.
     *
     * @param query filters the transfers, or null for all of them
     * @return the transfers that match the query
     */
    fun getTransfers(query: MoneroTransferQuery? = null): List<MoneroTransfer>

    /**
     * Get incoming and outgoing transfers of an account, or of one of its subaddresses.
     *
     * @param accountIdx is the index of the account
     * @param subaddressIdx is the index of the subaddress within the account, or null for the whole account
     * @return the transfers of the account or of the subaddress
     */
    fun getTransfers(accountIdx: Int, subaddressIdx: Int? = null): List<MoneroTransfer>

    /**
     * Get the outputs of the wallet.
     *
     * @param query filters the outputs, or null for all of them
     * @return the outputs that match the query
     */
    fun getOutputs(query: MoneroOutputQuery? = null): List<MoneroOutputWallet>

    /**
     * Export outputs in hex format.
     *
     * @param all exports all the outputs if true, or only the ones that changed since the last export if false
     * @return the outputs in hex format
     */
    fun exportOutputs(all: Boolean = false): String

    /**
     * Import outputs in hex format.
     *
     * @param outputsHex are outputs in hex format
     * @return the number of outputs imported
     */
    fun importOutputs(outputsHex: String): Int

    /**
     * Export signed key images.
     *
     * @param all exports all key images if true, or only the ones that changed since the last export if false
     * @return the wallet's signed key images
     */
    fun exportKeyImages(all: Boolean = false): MoneroKeyImageExportResult

    /**
     * Import signed key images and verify their spent status.
     *
     * @param keyImages are images to import and verify
     * @param offset is the offset of the first key image among the wallet's outputs
     * @return the results of the import
     */
    fun importKeyImages(keyImages: List<MoneroKeyImage>, offset: Long = 0): MoneroKeyImageImportResult

    /**
     * Freeze an output so that it is not spent.
     *
     * @param keyImage is the key image of the output to freeze
     */
    fun freezeOutput(keyImage: String)

    /**
     * Thaw a frozen output so that it can be spent.
     *
     * @param keyImage is the key image of the output to thaw
     */
    fun thawOutput(keyImage: String)

    /**
     * Check if an output is frozen.
     *
     * @param keyImage is the key image of the output to check
     * @return true if the output is frozen, false otherwise
     */
    fun isOutputFrozen(keyImage: String): Boolean

    /**
     * Create a transaction to transfer funds from this wallet.
     *
     * @param config configures the transaction to create
     * @return the created transaction
     */
    fun createTx(config: MoneroTxConfig): MoneroTxWallet

    /**
     * Create one or more transactions to transfer funds from this wallet.
     *
     * @param config configures the transactions to create
     * @return the created transactions
     */
    fun createTxs(config: MoneroTxConfig): List<MoneroTxWallet>

    /**
     * Sweep an output with a given key image.
     *
     * @param config configures the transaction to create
     * @return the created transaction
     */
    fun sweepOutput(config: MoneroTxConfig): MoneroTxWallet

    /**
     * Sweep all unlocked funds according to the given configuration.
     *
     * @param config configures the transactions to create
     * @return the created transactions
     */
    fun sweepUnlocked(config: MoneroTxConfig): List<MoneroTxWallet>

    /**
     * Sweep all unmixable dust outputs back to the wallet to make them easier to spend and mix.
     *
     * @param relay specifies if the resulting transaction should be relayed
     * @return the created transactions
     */
    fun sweepDust(relay: Boolean = false): List<MoneroTxWallet>

    /**
     * Relay a previously created transaction.
     *
     * @param txMetadata is the metadata of the transaction to relay
     * @return the hash of the relayed transaction
     */
    fun relayTx(txMetadata: String): String

    /**
     * Relay a previously created transaction.
     *
     * @param tx is the transaction to relay, with its metadata
     * @return the hash of the relayed transaction
     */
    fun relayTx(tx: MoneroTxWallet): String

    /**
     * Relay previously created transactions.
     *
     * @param txMetadatas are the metadata of the transactions to relay
     * @return the hashes of the relayed transactions
     */
    fun relayTxs(txMetadatas: List<String>): List<String>

    /**
     * Relay previously created transactions.
     *
     * @param txs are the transactions to relay, with their metadata
     * @return the hashes of the relayed transactions
     */
    fun relayTxs(txs: Collection<MoneroTxWallet>): List<String>

    /**
     * Describe a tx set containing unsigned or multisig tx hex to a new tx set containing structured transactions.
     *
     * @param txSet is a tx set containing unsigned or multisig tx hex
     * @return the tx set with the structured transactions
     */
    fun describeTxSet(txSet: MoneroTxSet): MoneroTxSet

    /**
     * Describe unsigned tx hex as a tx set with structured transactions.
     *
     * @param unsignedTxHex is unsigned tx hex from when the transactions were created
     * @return the tx set with the structured transactions
     */
    fun describeUnsignedTxSet(unsignedTxHex: String): MoneroTxSet

    /**
     * Describe multisig tx hex as a tx set with structured transactions.
     *
     * @param multisigTxHex is multisig tx hex from when the transactions were created
     * @return the tx set with the structured transactions
     */
    fun describeMultisigTxSet(multisigTxHex: String): MoneroTxSet

    /**
     * Sign unsigned transactions from a view-only wallet.
     *
     * @param unsignedTxHex is unsigned tx hex from when the transactions were created
     * @return the tx set with the signed transactions and their hex
     */
    fun signTxs(unsignedTxHex: String): MoneroTxSet

    /**
     * Submit signed transactions from a view-only wallet.
     *
     * @param signedTxHex is signed tx hex from signing the transactions
     * @return the hashes of the submitted transactions
     */
    fun submitTxs(signedTxHex: String): List<String>

    /**
     * Get a transaction note.
     *
     * @param txHash specifies the transaction to get the note of
     * @return the tx note
     */
    fun getTxNote(txHash: String): String

    /**
     * Get the notes of multiple transactions.
     *
     * @param txHashes identify the transactions to get the notes of
     * @return the notes, in the order of the hashes
     */
    fun getTxNotes(txHashes: List<String>): List<String>

    /**
     * Set a note for a specific transaction.
     *
     * @param txHash specifies the transaction
     * @param note specifies the note
     */
    fun setTxNote(txHash: String, note: String)

    /**
     * Set notes for multiple transactions.
     *
     * @param txHashes specify the transactions to set notes for
     * @param notes are the notes to set, one for each hash
     */
    fun setTxNotes(txHashes: List<String>, notes: List<String>)

    /**
     * Get the entries of the address book.
     *
     * @param indices are the indices of the entries to get, or null for all of them
     * @return the address book entries
     */
    fun getAddressBookEntries(indices: List<Long>? = null): List<MoneroAddressBookEntry>

    /**
     * Add an entry to the address book.
     *
     * @param address is the address of the entry
     * @param description is the description of the entry, or null for none
     * @return the index of the added entry
     */
    fun addAddressBookEntry(address: String, description: String? = null): Long

    /**
     * Edit an entry of the address book.
     *
     * @param index is the index of the entry to edit
     * @param address is the new address of the entry, or null to leave it
     * @param description is the new description of the entry, or null to leave it
     */
    fun editAddressBookEntry(index: Long, address: String?, description: String?)

    /**
     * Delete an entry of the address book.
     *
     * @param index is the index of the entry to delete
     */
    fun deleteAddressBookEntry(index: Long)

    /**
     * Get a payment URI from a transaction config.
     *
     * @param config specifies configuration for a potential transaction
     * @return the payment uri
     */
    fun getPaymentUri(config: MoneroTxConfig): String

    /**
     * Parse a payment URI to a transaction config.
     *
     * @param uri is the payment uri to parse
     * @return the transaction config
     */
    fun parsePaymentUri(uri: String): MoneroTxConfig

    /**
     * Sign a message.
     *
     * @param message is the message to sign
     * @param signatureType sign with the spend key or the view key
     * @param accountIdx is the index of the account whose key signs the message
     * @param subaddressIdx is the index of the subaddress within the account
     * @return the signature
     */
    fun signMessage(
        message: String,
        signatureType: MoneroMessageSignatureType = MoneroMessageSignatureType.SIGN_WITH_SPEND_KEY,
        accountIdx: Int = 0,
        subaddressIdx: Int = 0
    ): String

    /**
     * Verify a signature on a message.
     *
     * @param message is the signed message
     * @param address is the signing address
     * @param signature is the signature
     * @return the result of the verification
     */
    fun verifyMessage(message: String, address: String, signature: String): MoneroMessageSignatureResult

    /**
     * Get a transaction's secret key from its hash.
     *
     * @param txHash is the transaction's hash
     * @return the transaction's secret key
     */
    fun getTxKey(txHash: String): String

    /**
     * Check a transaction in the blockchain with its secret key.
     *
     * @param txHash specifies the transaction to check
     * @param txKey is the transaction's secret key
     * @param address is the destination public address of the transaction
     * @return the result of the check
     */
    fun checkTxKey(txHash: String, txKey: String, address: String): MoneroCheckTx

    /**
     * Get a transaction signature to prove it.
     *
     * @param txHash specifies the transaction to prove
     * @param address is the destination public address of the transaction
     * @param message is a message to include with the signature to further authenticate the proof, or null for none
     * @return the transaction signature
     */
    fun getTxProof(txHash: String, address: String, message: String? = null): String

    /**
     * Prove a transaction by checking its signature.
     *
     * @param txHash specifies the transaction to prove
     * @param address is the destination public address of the transaction
     * @param message is a message included with the signature, or null for none
     * @param signature is the transaction signature to confirm
     * @return the result of the check
     */
    fun checkTxProof(txHash: String, address: String, message: String?, signature: String): MoneroCheckTx

    /**
     * Generate a signature to prove a spend. Unlike proving a transaction, it does not require the destination public
     * address.
     *
     * @param txHash specifies the transaction to prove
     * @param message is a message to include with the signature to further authenticate the proof, or null for none
     * @return the transaction signature
     */
    fun getSpendProof(txHash: String, message: String? = null): String

    /**
     * Prove a spend using a signature. Unlike proving a transaction, it does not require the destination public
     * address.
     *
     * @param txHash specifies the transaction to prove
     * @param message is a message included with the signature, or null for none
     * @param signature is the transaction signature to confirm
     * @return true if the signature is good, false otherwise
     */
    fun checkSpendProof(txHash: String, message: String?, signature: String): Boolean

    /**
     * Generate a signature to prove the entire balance of the wallet.
     *
     * @param message is a message included with the signature to further authenticate the proof, or null for none
     * @return the reserve proof signature
     */
    fun getReserveProofWallet(message: String? = null): String

    /**
     * Generate a signature to prove an available amount in an account.
     *
     * @param accountIdx specifies the account to prove ownership of the amount
     * @param amount is the minimum amount to prove as available in the account
     * @param message is a message to include with the signature to further authenticate the proof, or null for none
     * @return the reserve proof signature
     */
    fun getReserveProofAccount(accountIdx: Int, amount: BigInteger, message: String? = null): String

    /**
     * Proof of a reserve using a signature.
     *
     * @param address is the public wallet address
     * @param message is a message included with the signature, or null for none
     * @param signature is the reserve proof signature to check
     * @return the result of the check
     */
    fun checkReserveProof(address: String, message: String?, signature: String): MoneroCheckReserve

    /**
     * Indicates if the wallet is a multisig wallet.
     *
     * @return true if the wallet is a multisig wallet, false otherwise
     */
    fun isMultisig(): Boolean

    /**
     * Get multisig info about this wallet.
     *
     * @return the multisig info of the wallet
     */
    fun getMultisigInfo(): MoneroMultisigInfo

    /**
     * Indicates if importing multisig data is needed for returning a correct balance.
     *
     * @return true if importing multisig data is needed, false otherwise
     */
    fun isMultisigImportNeeded(): Boolean

    /**
     * Get multisig info as hex to share with participants to begin creating a multisig wallet.
     *
     * @return this wallet's multisig hex to share with the other participants
     */
    fun prepareMultisig(): String

    /**
     * Make this wallet multisig by importing multisig hex from participants.
     *
     * @param multisigHexes are multisig hex from each participant
     * @param threshold is the number of signatures needed to sign transfers
     * @param password is the wallet password
     * @return this wallet's multisig hex to share with the other participants if another step is needed
     */
    fun makeMultisig(multisigHexes: List<String>, threshold: Int, password: String): String

    /**
     * Exchange multisig hex with participants in a multisig wallet.
     *
     * @param multisigHexes are multisig hex from each participant
     * @param password is the wallet's password
     * @return the result with the multisig wallet's address once it is initialized, or the hex to share with the
     *      participants otherwise
     */
    fun exchangeMultisigKeys(multisigHexes: List<String>, password: String): MoneroMultisigInitResult

    /**
     * Export this wallet's multisig info as hex for other participants.
     *
     * @return this wallet's multisig info as hex for other participants
     */
    fun exportMultisigHex(): String

    /**
     * Import multisig info as hex from other participants.
     *
     * @param multisigHexes are multisig hex from each participant
     * @param refreshAfterImport refreshes the wallet after the import if true
     * @return the number of outputs signed with the given multisig hex
     */
    fun importMultisigHex(multisigHexes: List<String>, refreshAfterImport: Boolean = true): Int

    /**
     * Sign multisig transactions from a multisig wallet.
     *
     * @param multisigTxHex represents unsigned multisig transactions as hex
     * @return the result of signing the multisig transactions
     */
    fun signMultisigTxHex(multisigTxHex: String): MoneroMultisigSignResult

    /**
     * Submit signed multisig transactions from a multisig wallet.
     *
     * @param signedMultisigTxHex represents signed multisig transactions as hex
     * @return the hashes of the submitted transactions
     */
    fun submitMultisigTxHex(signedMultisigTxHex: String): List<String>
}
