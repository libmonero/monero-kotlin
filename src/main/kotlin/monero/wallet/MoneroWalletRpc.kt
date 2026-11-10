package monero.wallet

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.common.MoneroRpcConnection
import monero.internal.MoneroNative
import monero.internal.SizeT
import monero.wallet.model.MoneroAccount
import monero.wallet.model.MoneroAccountTag
import monero.wallet.model.MoneroWalletConfig

/**
 * Implements a Monero wallet that lives on a monero-wallet-rpc server: the keys, the files and the sync are the server's, and the
 * wallet asks the server for them over its RPC. It is the RPC wallet of monero-cpp behind the C ABI of monero-c, connected to the server
 * by its constructor, and it has no wallet open until [openWallet] or [createWallet]. The functions that a wallet with a handle shares
 * are the ones of [MoneroWalletNative]. Close it to release the native handle, which closes its wallet on the server.
 *
 * @param rpc is the connection to the server
 * @throws MoneroError if the native library is not found, or it can not make the connection
 */
class MoneroWalletRpc(rpc: MoneroRpcConnection) : MoneroWalletNative(connect(rpc), "MoneroWalletRpc") {

    /**
     * Construct a wallet that connects to a server.
     *
     * @param uri is the URI of the server
     * @param username is the username of the server, or null for none
     * @param password is the password of the server, or null for none
     */
    constructor(uri: String, username: String? = null, password: String? = null) : this(MoneroRpcConnection(uri, username, password))

    // The close of the wallet is a request to the server, which does not answer when its network is down: the handle is freed anyway, and the
    // error is thrown. A server that has no wallet open has nothing to close, and that is not an error
    override fun onCloseFailed(error: MoneroError): MoneroError? {
        return if (error.message == NO_WALLET_FILE) null else error
    }

    // ------------------------------- THE SERVER ----------------------------------

    /**
     * Open a wallet of the server, by its name.
     *
     * @param name is the name of the wallet on the server
     * @param password is the password of the wallet, or null for none
     * @throws MoneroError if the wallet is closed, the server has no wallet of that name, or the password is not its password
     */
    fun openWallet(name: String, password: String? = null) {
        checkNotClosed()
        run { lib, h -> MoneroNative.ok(lib.monero_wallet_rpc_open_wallet(h, name, password)) }
    }

    /**
     * Open a wallet of the server, which the config names with its path, and the password of the wallet.
     *
     * @param config is the configuration of the wallet, which needs its path
     * @throws MoneroError if the wallet is closed, the config has no path, the server has no wallet of that name, or the password is not
     * its password
     */
    fun openWallet(config: MoneroWalletConfig) {
        openWallet(config.path ?: throw MoneroError("Must provide name of wallet to open"), config.password)
    }

    /**
     * Create a wallet on the server and open it. A config with a seed restores the wallet from it, a config with keys restores it from
     * them, and a config with neither makes a random wallet. The network is the one of the server, so the config does not give one.
     *
     * @param config is the configuration of the wallet, which needs its path, the name of the wallet on the server
     * @throws MoneroError if the wallet is closed, or the server can not create it: a wallet of that name exists already, or the seed
     * or the keys are not valid
     */
    fun createWallet(config: MoneroWalletConfig) {
        checkNotClosed()
        if (config.path == null) throw MoneroError("Must provide name of wallet to create")
        run { lib, h -> MoneroNative.ok(lib.monero_wallet_rpc_create_wallet(h, MoneroJson.serialize(config))) }
    }

    /**
     * Get the connection to the server, as it is now: a copy, with the last known state of the server.
     *
     * @return the connection
     * @throws MoneroError if the wallet is closed
     */
    fun getRpcConnection(): MoneroRpcConnection {
        return json<MoneroRpcConnection> { lib, h, out -> lib.monero_wallet_rpc_get_connection(h, out) }
    }

    /**
     * Get the languages that the server writes a seed in.
     *
     * @return the names of the languages
     * @throws MoneroError if the wallet is closed, or the server does not answer
     */
    fun getSeedLanguages(): List<String> {
        checkNotClosed()
        return run { lib, h ->
            val out = PointerByReference()
            MoneroNative.strings(lib.monero_wallet_rpc_get_seed_languages(h, out), out)
        }
    }

    /**
     * Set how often the wallet polls the server for changes, which it begins to do with the first call after this.
     *
     * @param periodMs is the period in milliseconds, from 1 to 2147483647
     * @throws MoneroError if the wallet is closed, or the period is not in that range
     */
    fun setPollPeriodInMs(periodMs: Long) {
        checkNotClosed()
        run { lib, h -> MoneroNative.ok(lib.monero_wallet_rpc_set_poll_period(h, periodMs)) }
    }

    /**
     * Save the wallet, close it, and stop the server. The calls of the wallet fail from then on, so close it after.
     *
     * @throws MoneroError if the wallet is closed, or the server does not answer
     */
    fun stop() {
        checkNotClosed()
        run { lib, h -> MoneroNative.ok(lib.monero_wallet_rpc_stop(h)) }
    }

    // ------------------------------- ACCOUNTS AND TAGS ----------------------------------

    /**
     * Get the accounts of the wallet, or the ones that a tag is on.
     *
     * @param includeSubaddresses is whether to include the subaddresses of each account
     * @param tag is the tag of the accounts to get, or null or "" for all of them
     * @return the accounts of the wallet
     * @throws MoneroError if the wallet is closed, or the server has no tag of that name
     */
    override fun getAccounts(includeSubaddresses: Boolean, tag: String?): List<MoneroAccount> {
        checkNotClosed()
        return json { lib, h, out -> lib.monero_wallet_get_accounts(h, includeSubaddresses, tag, false, out) }
    }

    /**
     * Get the tags of the accounts, with their labels and the accounts that each of them is on.
     *
     * @return the tags
     * @throws MoneroError if the wallet is closed
     */
    override fun getAccountTags(): List<MoneroAccountTag> {
        checkNotClosed()
        return json { lib, h, out -> lib.monero_wallet_get_account_tags(h, out) }
    }

    /**
     * Put a tag on accounts. An account has one tag, so a tag on an account that has another replaces it.
     *
     * @param tag is the tag
     * @param accountIndices are the indices of the accounts to tag
     * @throws MoneroError if the wallet is closed, or an account is not there
     */
    override fun tagAccounts(tag: String, accountIndices: List<Int>) {
        checkNotClosed()
        val indices = accountIndices.toIntArray()
        run { lib, h -> MoneroNative.ok(lib.monero_wallet_tag_accounts(h, tag, indices, SizeT(indices.size.toLong()))) }
    }

    /**
     * Take the tag off accounts.
     *
     * @param accountIndices are the indices of the accounts to untag
     * @throws MoneroError if the wallet is closed, or an account is not there
     */
    override fun untagAccounts(accountIndices: List<Int>) {
        checkNotClosed()
        val indices = accountIndices.toIntArray()
        run { lib, h -> MoneroNative.ok(lib.monero_wallet_untag_accounts(h, indices, SizeT(indices.size.toLong()))) }
    }

    /**
     * Set the label of a tag.
     *
     * @param tag is the tag
     * @param label is the label of the tag
     * @throws MoneroError if the wallet is closed, or the tag is not one that the server has
     */
    override fun setAccountTagLabel(tag: String, label: String) {
        checkNotClosed()
        run { lib, h -> MoneroNative.ok(lib.monero_wallet_set_account_tag_label(h, tag, label)) }
    }

    private companion object {

        const val NO_WALLET_FILE = "No wallet file"

        // connects to the server, with a copy of the connection of the native library, which is freed after the call
        fun connect(rpc: MoneroRpcConnection): Pointer {
            val lib = MoneroNative.lib
            val connection = PointerByReference()
            MoneroNative.ok(lib.monero_rpc_connection_create(MoneroJson.serialize(rpc), connection))
            try {
                val wallet = PointerByReference()
                MoneroNative.ok(lib.monero_wallet_rpc_connect(connection.value, wallet))
                return wallet.value
            } finally {
                lib.monero_rpc_connection_free(connection.value)
            }
        }
    }
}
