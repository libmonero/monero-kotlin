package monero.wallet

import com.sun.jna.Pointer
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.PointerByReference
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.internal.MoneroC
import monero.internal.MoneroNative
import monero.wallet.model.MoneroAccount
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
 * handle. [requestShutdown] is the one call that any thread can make, to stop a sync that is running.
 */
class MoneroWalletFull private constructor(handle: Pointer) : MoneroWalletNative(handle, "MoneroWalletFull") {

    /**
     * Get the languages that a seed can be written in. The call needs no wallet, since monero-c has the list of the
     * languages itself, so it answers on a closed wallet too.
     *
     * @return the names of the languages
     * @throws MoneroError if the native library fails
     */
    fun getSeedLanguages(): List<String> {
        val out = PointerByReference()
        return MoneroNative.strings(MoneroNative.lib.monero_wallet_get_seed_languages(out), out)
    }

    /**
     * Get the accounts of the wallet.
     *
     * @param includeSubaddresses is whether to include the subaddresses of each account
     * @param tag is not supported: a full wallet has no tags of accounts, so it has to be null
     * @return the accounts of the wallet
     * @throws MoneroError if the wallet is closed, or a tag is given
     */
    override fun getAccounts(includeSubaddresses: Boolean, tag: String?): List<MoneroAccount> {
        checkNotClosed()
        if (tag != null) throw MoneroError("The accounts can not be asked by a tag: a full wallet has no tags of accounts")
        return json { lib, h, out -> lib.monero_wallet_get_accounts(h, includeSubaddresses, out) }
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
         * Check if a wallet is at a path. It looks for the keys file, path + ".keys". The call needs no wallet, so it
         * runs on the thread of the caller: there is no worker to order it.
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
