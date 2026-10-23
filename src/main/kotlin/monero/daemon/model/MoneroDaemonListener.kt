package monero.daemon.model

/**
 * Receives notifications as a daemon is updated.
 */
open class MoneroDaemonListener {

    /**
     * Called when a new block is added to the chain.
     *
     * @param header is the header of the block added to the chain
     */
    open fun onBlockHeader(header: MoneroBlockHeader) {
        // nothing to do
    }
}
