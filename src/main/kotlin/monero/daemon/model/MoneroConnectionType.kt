package monero.daemon.model

/**
 * Enumerates the types of address of a connection of the daemon. The numbers are the ones of monero-cpp.
 */
enum class MoneroConnectionType {
    INVALID,
    IPV4,
    IPV6,
    TOR,
    I2P
}
