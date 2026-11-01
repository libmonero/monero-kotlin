package monero.wallet

import com.fasterxml.jackson.core.type.TypeReference
import com.sun.jna.Pointer
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.daemon.model.MoneroNetworkType
import monero.daemon.model.MoneroVersion
import monero.internal.MoneroC
import monero.internal.MoneroNative
import monero.internal.NativeWorker
import monero.wallet.model.MoneroWalletConfig

/**
 * Implements a full Monero wallet: the keys and the addresses of an account, the chain of a daemon, and its own wallet
 * files on disk. It is the full wallet of monero-cpp behind the C ABI of monero-c, created with one of its three
 * constructors ([createWalletRandom], [createWalletFromSeed] and [createWalletFromKeys]) or from a config
 * ([createWallet]), and opened from its files ([openWallet]). Close it to release the native handle.
 *
 * The wallet files hold the chain that the wallet has scanned: [save] writes them, [close] with a save writes them
 * before closing, and a wallet that is opened again reads its chain from them, which is why it does not have to scan
 * the chain again. A wallet that a config creates without a path stays in memory.
 *
 * The calls of a wallet run one after the other on a thread of their own, since the C ABI allows one thread per wallet
 * handle.
 */
class MoneroWalletFull private constructor(private val handle: Pointer) : AutoCloseable {

    private val worker = NativeWorker("MoneroWalletFull")
    private var closed = false

    // ------------------------------- THE FILES ----------------------------------

    /**
     * Get the path of the wallet file, which the wallet was created with, or "" when it stays in memory.
     *
     * @return the path
     * @throws MoneroError if the wallet is closed
     */
    fun getPath(): String {
        return string { lib, h, out -> lib.monero_wallet_get_path(h, out) }
    }

    /**
     * Write the wallet to its files, which a wallet that stays in memory has none of.
     *
     * @throws MoneroError if the wallet is closed, or it has no path to write to
     */
    fun save() {
        checkNotClosed()
        requirePath()
        run { lib, h -> MoneroNative.ok(lib.monero_wallet_save(h)) }
    }

    /**
     * Change the password of the wallet files, which are written with it, and which the wallet opens with from then
     * on.
     *
     * @param oldPassword is the password of the files, which has to be the one that they are written with
     * @param newPassword is the password to write the files with
     * @throws MoneroError if the wallet is closed, or the old password is not the one that the files are written with
     */
    fun changePassword(oldPassword: String, newPassword: String) {
        checkNotClosed()
        run { lib, h -> MoneroNative.ok(lib.monero_wallet_change_password(h, oldPassword, newPassword)) }
    }

    // ------------------------------- STATE ----------------------------------

    /**
     * Get the network of the wallet.
     *
     * @return the network type
     * @throws MoneroError if the wallet is closed
     */
    fun getNetworkType(): MoneroNetworkType {
        checkNotClosed()
        return run { lib, h ->
            val networkType = IntByReference()
            MoneroNative.ok(lib.monero_wallet_get_network_type(h, networkType))
            MoneroNetworkType.values()[networkType.value]
        }
    }

    /**
     * Check if the wallet is view-only, which means it has no private spend key.
     *
     * @return true if the wallet is view-only, false otherwise
     * @throws MoneroError if the wallet is closed
     */
    fun isViewOnly(): Boolean {
        checkNotClosed()
        return run { lib, h ->
            val viewOnly = ByteByReference()
            MoneroNative.ok(lib.monero_wallet_is_view_only(h, viewOnly))
            viewOnly.value.toInt() != 0
        }
    }

    /**
     * Get the version of the wallet library.
     *
     * @return the version
     * @throws MoneroError if the wallet is closed
     */
    fun getVersion(): MoneroVersion {
        checkNotClosed()
        return json { lib, h, out -> lib.monero_wallet_get_version(h, out) }
    }

    /**
     * Get the height that the wallet scans from when it is created or restored, which is the first block that it
     * scans.
     *
     * @return the restore height
     * @throws MoneroError if the wallet is closed
     */
    fun getRestoreHeight(): Long {
        checkNotClosed()
        return run { lib, h ->
            val height = LongByReference()
            MoneroNative.ok(lib.monero_wallet_get_restore_height(h, height))
            height.value
        }
    }

    /**
     * Set the height that the wallet scans from, which it does before its next scan: a height that it has already
     * scanned makes it scan the chain from it again.
     *
     * @param restoreHeight is the height to scan from
     * @throws MoneroError if the wallet is closed
     */
    fun setRestoreHeight(restoreHeight: Long) {
        checkNotClosed()
        run { lib, h -> MoneroNative.ok(lib.monero_wallet_set_restore_height(h, restoreHeight)) }
    }

    // ------------------------------- CLOSING ----------------------------------

    /**
     * Check if the wallet is closed.
     *
     * @return true if the wallet is closed, false otherwise
     */
    fun isClosed(): Boolean {
        return closed
    }

    /**
     * Close the wallet, writing it to its files first when a save is asked for. Every call of a closed wallet fails
     * with "Wallet is closed", and closing it again does nothing.
     *
     * @param save is whether to save the wallet before closing it
     * @throws MoneroError if a save is asked for and the wallet has no path to write to, in which case it stays open
     */
    @Synchronized
    fun close(save: Boolean) {
        if (closed) return
        if (save) requirePath()
        run { lib, h -> MoneroNative.ok(lib.monero_wallet_close(h, save)) }
        closed = true
        worker.close()
        MoneroNative.lib.monero_wallet_free(handle)
    }

    override fun close() {
        close(false)
    }

    // ------------------------------- PRIVATE ------------------------------------

    private fun checkNotClosed() {
        if (closed) throw MoneroError("Wallet is closed")
    }

    // wallet2 skips the save of a wallet that stays in memory without a word, which loses the wallet when it closes
    private fun requirePath() {
        if (getPath().isEmpty()) throw MoneroError("The wallet has no path to write to")
    }

    private fun <T> run(call: (MoneroC, Pointer) -> T): T {
        return worker.call { call(MoneroNative.lib, handle) }
    }

    private inline fun <reified T> json(crossinline call: (MoneroC, Pointer, PointerByReference) -> Int): T {
        return run { lib, h ->
            val out = PointerByReference()
            MoneroNative.json(call(lib, h, out), out, object : TypeReference<T>() {})
        }
    }

    private fun string(call: (MoneroC, Pointer, PointerByReference) -> Int): String {
        checkNotClosed()
        return run { lib, h ->
            val out = PointerByReference()
            MoneroNative.text(call(lib, h, out), out) ?: throw MoneroError("monero-c returned no string")
        }
    }

    companion object {

        /**
         * The language that a wallet writes its seed in when none is given.
         */
        const val DEFAULT_LANGUAGE = "English"

        /**
         * Create a wallet with a new random seed, and write it to its files. The keys are written to path + ".keys".
         *
         * @param config is the configuration of the wallet, which needs its path and its network type, and can name
         * the password of the files and the language of the seed
         * @return the new wallet
         * @throws MoneroError if the configuration has no path or no network type, its language is not a language of
         * a seed, or a wallet is at the path
         */
        fun createWalletRandom(config: MoneroWalletConfig): MoneroWalletFull {
            val path = config.path ?: throw MoneroError("Must provide wallet path")
            val networkType = config.networkType ?: throw MoneroError("Must provide wallet network type")
            return wallet { lib, out -> lib.monero_wallet_create_random(path, config.password, networkType.ordinal, config.language, out) }
        }

        /**
         * Restore a wallet from its mnemonic seed, and write it to its files.
         *
         * @param config is the configuration of the wallet, which needs its path, its network type and its seed, and
         * can give the seed an offset, which is a passphrase that extends the seed, name the password of the files
         * and the language of the seed, and give the restore height, which is the first block to scan
         * @return the restored wallet
         * @throws MoneroError if the configuration has no path, no network type or no seed, its seed is not a
         * mnemonic, its language is not a language of a seed, or a wallet is at the path
         */
        fun createWalletFromSeed(config: MoneroWalletConfig): MoneroWalletFull {
            val path = config.path ?: throw MoneroError("Must provide wallet path")
            val networkType = config.networkType ?: throw MoneroError("Must provide wallet network type")
            val seed = config.seed ?: throw MoneroError("Must provide wallet seed")
            return wallet { lib, out -> lib.monero_wallet_create_from_seed(path, config.password, networkType.ordinal, seed, config.seedOffset, config.restoreHeight ?: 0, config.language, out) }
        }

        /**
         * Restore a wallet from its keys, and write it to its files. Without a private spend key the wallet is
         * view-only.
         *
         * @param config is the configuration of the wallet, which needs its path, its network type and its keys,
         * and can name the password of the files and the language of the seed, and give the restore height, which is
         * the first block to scan
         * @return the restored wallet
         * @throws MoneroError if the configuration has no path or no network type, no key to restore from (which the
         * wrapper answers as a random wallet with a restore height, "Cannot specify restore height when creating
         * random wallet"), a private view key without the address, keys that do not match the address, or a wallet
         * is at the path
         */
        fun createWalletFromKeys(config: MoneroWalletConfig): MoneroWalletFull {
            val path = config.path ?: throw MoneroError("Must provide wallet path")
            val networkType = config.networkType ?: throw MoneroError("Must provide wallet network type")

            // the C ABI takes the address and the keys as strings that are never null, empty when they are unused,
            // which the full wallet of monero-cpp reads as absent
            val address = config.primaryAddress ?: ""
            val privateViewKey = config.privateViewKey ?: ""
            val privateSpendKey = config.privateSpendKey ?: ""
            return wallet { lib, out -> lib.monero_wallet_create_from_keys(path, config.password, networkType.ordinal, address, privateViewKey, privateSpendKey, config.restoreHeight ?: 0, config.language, out) }
        }

        /**
         * Create a wallet from a config, which the other constructors cannot: it can connect the wallet to a daemon
         * when it is created, set the trust in the daemon, scan more accounts and subaddresses than the 50 and 200 of
         * wallet2, restore a multisig wallet, and leave the wallet in memory, which it is without a path.
         *
         * @param config is the configuration of the wallet, which needs its network type
         * @return the new wallet
         * @throws MoneroError if the configuration is invalid: a seed and keys together fail, and so does a restore
         * height with neither of them, since a random wallet starts from the current height
         */
        fun createWallet(config: MoneroWalletConfig): MoneroWalletFull {
            if (config.networkType == null) throw MoneroError("Must provide wallet network type")
            return wallet { lib, out -> lib.monero_wallet_create(MoneroJson.serialize(config), out) }
        }

        /**
         * Open a wallet from its files on disk.
         *
         * @param config is the configuration of the wallet, which needs its path and its network type, and can name
         * the password of the files
         * @return the opened wallet
         * @throws MoneroError if the configuration has no path or no network type, no wallet is at the path, or the
         * password is not the one of the files
         */
        fun openWallet(config: MoneroWalletConfig): MoneroWalletFull {
            val path = config.path ?: throw MoneroError("Must provide wallet path")
            val networkType = config.networkType ?: throw MoneroError("Must provide wallet network type")
            return wallet { lib, out -> lib.monero_wallet_open(path, config.password, networkType.ordinal, out) }
        }

        /**
         * Check if a wallet is at a path. It looks for the keys file, path + ".keys".
         *
         * @param path is the path of the wallet file
         * @return true if the wallet exists, false otherwise
         * @throws MoneroError if the native library fails
         */
        fun walletExists(path: String): Boolean {
            val exists = ByteByReference()
            MoneroNative.ok(MoneroNative.lib.monero_wallet_exists(path, exists))
            return exists.value.toInt() != 0
        }

        private fun wallet(create: (MoneroC, PointerByReference) -> Int): MoneroWalletFull {
            val lib = MoneroNative.lib
            val out = PointerByReference()
            MoneroNative.ok(create(lib, out))
            return MoneroWalletFull(out.value)
        }
    }
}
