package monero.daemon.model

/**
 * Enumerates a key image spent status.
 */
enum class MoneroKeyImageSpentStatus {

    /**
     * Indicates that the key image is not spent on the blockchain.
     */
    NOT_SPENT,

    /**
     * Indicates that the key image was spent on the blockchain.
     */
    CONFIRMED,

    /**
     * Indicates that the key image is spent but still in the transaction pool.
     */
    TX_POOL
}
