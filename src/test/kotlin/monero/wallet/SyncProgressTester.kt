package monero.wallet

import java.util.Collections
import monero.wallet.model.MoneroWalletListener

/**
 * Tests the progress that a wallet notifies as it syncs, like SyncProgressTester of monero-python: the heights advance, the start height
 * only goes down, the chain only grows, and the percentage is the one that the heights say. A notification runs on a thread of the
 * wallet, where an assertion that fails would be lost, so the checks are kept in [errors] for the test to look at.
 */
class SyncProgressTester(private val wallet: MoneroWalletNative, private var startHeight: Long, private var prevEndHeight: Long) : MoneroWalletListener() {

    val errors: MutableList<Throwable> = Collections.synchronizedList(ArrayList())

    /** the height of the last notification, or null if there was none */
    @Volatile
    var prevHeight: Long? = null
        private set

    @Volatile
    private var prevCompleteHeight: Long? = null

    private var sessionStart = true

    @Volatile
    private var isDone = false

    /** whether the listener was notified after [onDone] */
    @Volatile
    var notifiedAfterDone = false
        private set

    init {
        require(startHeight >= 0) { "Invalid start height provided: $startHeight" }
        require(prevEndHeight >= 0) { "Invalid end height provided: $prevEndHeight" }
    }

    override fun onSyncProgress(height: Long, startHeight: Long, endHeight: Long, percentDone: Double, message: String?) {
        try {
            // a registered listener is notified of the syncs after the first one
            if (isDone) {
                check(this in wallet.getListeners()) { "Listener has completed and is not registered so should not be called again" }
                notifiedAfterDone = true
            }

            // a new session of the sync starts the tester again
            val complete = prevCompleteHeight
            if (sessionStart && complete != null && startHeight >= complete) this.startHeight = startHeight

            check(endHeight > startHeight) { "end height > start height" }
            check(startHeight <= this.startHeight) { "start height only rebases down" }
            this.startHeight = startHeight
            check(endHeight >= prevEndHeight) { "chain can only grow while syncing" }
            prevEndHeight = endHeight
            prevHeight?.let { check(height >= it) { "heights advance monotonically" } }
            prevHeight = height

            if (height < startHeight) {
                check(sessionStart) { "hash-skip notification only at the start of a sync session" }
                check(percentDone == 0.0) { "the wallet skips ahead to the sync start with 0% done: $percentDone" }
            } else {
                check(height < endHeight) { "height < end height: $height, $endHeight" }
                val expected = (height - startHeight + 1).toDouble() / (endHeight - startHeight)
                check(expected == percentDone) { "expected $expected done, got $percentDone" }
                // the completion height is the one of the next session
                if (percentDone == 1.0) prevCompleteHeight = endHeight
            }

            // completion starts a new session
            sessionStart = percentDone == 1.0
        } catch (e: Throwable) {
            errors.add(e)
        }
    }

    /**
     * Test that the sync is done at the height of the chain: the last notification reports the last block.
     */
    fun onDone(chainHeight: Long) {
        check(!isDone)
        isDone = true
        val last = prevHeight
        if (last == null) {
            check(prevCompleteHeight == null)
            check(chainHeight == startHeight) { "no notification, and the chain is at $chainHeight, not at $startHeight" }
        } else {
            check(chainHeight - 1 == last) { "the last notification is at $last, and the chain is at $chainHeight" }
            check(chainHeight == prevCompleteHeight) { "the last sync ended at $prevCompleteHeight, not at $chainHeight" }
        }
    }
}
