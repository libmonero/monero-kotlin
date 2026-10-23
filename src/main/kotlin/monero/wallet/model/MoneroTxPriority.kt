package monero.wallet.model

/**
 * Enumerates the priorities of the fee of a transaction. The numbers are the ones of monero-cpp, and the JSON has them
 * as numbers.
 */
enum class MoneroTxPriority {
    DEFAULT,
    UNIMPORTANT,
    NORMAL,
    ELEVATED
}
