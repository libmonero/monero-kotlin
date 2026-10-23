package monero.wallet.model

/**
 * Enumerates the types of address. The numbers are the ones of monero-cpp, and the JSON has them as numbers.
 */
enum class MoneroAddressType {
    PRIMARY_ADDRESS,
    INTEGRATED_ADDRESS,
    SUBADDRESS
}
