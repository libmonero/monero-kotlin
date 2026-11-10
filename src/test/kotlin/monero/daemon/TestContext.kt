package monero.daemon

/**
 * What a test of a transaction or of a block expects of it, like TestContext of monero-python. A field that is null is not checked, except
 * for the three that [DaemonTxAssertions] needs to know: whether the transaction is pruned, whether it is confirmed and whether it comes
 * from the pool.
 */
open class TestContext {

    var isPruned: Boolean? = null
    var isConfirmed: Boolean? = null
    var fromGetTxPool: Boolean? = null
    var fromBinaryBlock: Boolean? = null
    var hasOutputIndices: Boolean? = null
    var hasTxs: Boolean? = null
    var hasHex: Boolean? = null
    var headerIsFull: Boolean? = null
    var txContext: TestContext? = null
}

/**
 * What a block that the daemon gives in a binary request has: no hex and no full header, and the transactions of the block, which are
 * confirmed, not pruned, with no output indices and no number of confirmations, like BinaryBlockContext of monero-python.
 */
class BinaryBlockContext : TestContext() {

    init {
        hasHex = false
        headerIsFull = false
        hasTxs = true
        txContext = TestContext().also {
            it.isPruned = false
            it.isConfirmed = true
            it.fromGetTxPool = false
            it.hasOutputIndices = false
            it.fromBinaryBlock = true
        }
    }
}
