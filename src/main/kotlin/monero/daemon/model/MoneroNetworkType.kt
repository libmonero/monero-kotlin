package monero.daemon.model

import monero.common.MoneroError

/**
 * Enumerates daemon networks. The numbers are the ones of monero-cpp, and the JSON has them as numbers.
 */
enum class MoneroNetworkType {
    MAINNET,
    TESTNET,
    STAGENET,

    // a private test chain on which the blocks are mined on demand. monero-cpp has it as a flag of the main network
    REGTEST;

    companion object {

        /**
         * Parses a network string to an enumerated type.
         *
         * @param network is the network string to parse, in any case: mainnet, testnet, stagenet or regtest
         * @return the enumerated network type
         */
        fun parse(network: String): MoneroNetworkType {
            return when (network.trim().lowercase()) {
                "mainnet" -> MAINNET
                "testnet" -> TESTNET
                "stagenet" -> STAGENET
                "regtest" -> REGTEST
                else -> throw MoneroError("Unknown network type: $network")
            }
        }
    }
}
