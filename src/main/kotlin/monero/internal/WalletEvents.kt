package monero.internal

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.wallet.model.MoneroWalletBlocksResult
import monero.wallet.model.MoneroWalletListener

/**
 * The native listener of a wallet, which calls a [MoneroWalletListener]. The callbacks of monero-c only queue what they get, and another
 * thread of the listener calls the listener, one notification after the other and in the order that the wallet made them. The callbacks
 * of the listener do not run on the thread of monero-c, since a call that the listener makes on the wallet waits for the sync that is
 * running on the thread of the wallet, and the sync waits for the callbacks of monero-c to return: with a thread between them, the sync
 * returns and the call goes on.
 */
internal class WalletEvents(private val listener: MoneroWalletListener) : AutoCloseable {

    private val queue = LinkedBlockingQueue<Any>()

    // when the listener last finished a notification, which a flush that waits for it counts its timeout from. It is set before the thread
    // of the listener starts
    @Volatile
    private var lastProgressNanos = System.nanoTime()

    // the native code holds the functions that the callbacks become, so the callbacks have to stay reachable
    private val onSyncProgress = object : WalletSyncProgressCallback {
        override fun invoke(userData: Pointer?, height: Long, startHeight: Long, endHeight: Long, percentDone: Double, message: Pointer?) {
            enqueue { SyncProgress(height, startHeight, endHeight, percentDone, message?.getString(0, "UTF-8")) }
        }
    }

    private val onNewBlock = object : WalletNewBlockCallback {
        override fun invoke(userData: Pointer?, height: Long) {
            enqueue { NewBlock(height) }
        }
    }

    private val onBalancesChanged = object : WalletBalancesChangedCallback {
        override fun invoke(userData: Pointer?, newBalance: Long, newUnlockedBalance: Long) {
            enqueue { BalancesChanged(newBalance, newUnlockedBalance) }
        }
    }

    private val onOutputReceived = object : WalletOutputCallback {
        override fun invoke(userData: Pointer?, outputJson: Pointer?) {
            enqueue { outputJson?.getString(0, "UTF-8")?.let { OutputReceived(it) } }
        }
    }

    private val onOutputSpent = object : WalletOutputCallback {
        override fun invoke(userData: Pointer?, outputJson: Pointer?) {
            enqueue { outputJson?.getString(0, "UTF-8")?.let { OutputSpent(it) } }
        }
    }

    /**
     * The native listener, to add to the wallet.
     */
    val handle: Pointer

    private val dispatcher: Thread

    init {
        val callbacks = WalletListenerCallbacks()
        callbacks.structSize = callbacks.size()
        callbacks.onSyncProgress = onSyncProgress
        callbacks.onNewBlock = onNewBlock
        callbacks.onBalancesChanged = onBalancesChanged
        callbacks.onOutputReceived = onOutputReceived
        callbacks.onOutputSpent = onOutputSpent
        val out = PointerByReference()
        MoneroNative.ok(MoneroNative.lib.monero_wallet_listener_create(callbacks, out))
        handle = out.value
        dispatcher = Thread(::dispatch, "MoneroWalletFull-events").apply {
            isDaemon = true
            start()
        }
    }

    // an error must not go back to the native code
    private inline fun enqueue(event: () -> Any?) {
        try {
            event()?.let { queue.offer(it) }
        } catch (_: Throwable) {
            // nothing to do
        }
    }

    private fun dispatch() {
        while (true) {
            val event = queue.take()
            if (event === STOP) return
            try {
                when (event) {
                    is SyncProgress -> listener.onSyncProgress(event.height, event.startHeight, event.endHeight, event.percentDone, event.message)
                    is NewBlock -> listener.onNewBlock(event.height)
                    is BalancesChanged -> listener.onBalancesChanged(MoneroNative.unsigned(event.balance), MoneroNative.unsigned(event.unlockedBalance))
                    is OutputReceived -> MoneroJson.deserialize(event.json, MoneroWalletBlocksResult::class.java).getOutputs().forEach { listener.onOutputReceived(it) }
                    is OutputSpent -> MoneroJson.deserialize(event.json, MoneroWalletBlocksResult::class.java).getInputs().forEach { listener.onOutputSpent(it) }
                    is Flush -> event.done.countDown()
                }
            } catch (_: Throwable) {
                // a listener that throws does not stop the next notifications
            } finally {
                lastProgressNanos = System.nanoTime()
            }
        }
    }

    /**
     * Wait until the listener has been called for everything that monero-c has notified so far, like a sync of monero-c waits for the
     * callbacks that it caused. It returns at once when it is called by the listener.
     *
     * The wait lasts as long as the listener takes to get through the notifications that are queued, however many they are, while it keeps
     * finishing them. A listener that finishes none for [idleTimeoutMs] is stuck, since it never returns from the one it has or it waits on a
     * lock of the thread that flushed, and it would hold that thread in a wait that never ends.
     *
     * @param idleTimeoutMs is how long the listener can go without finishing a notification while the flush waits for it, counted from the
     * last one that it finished, or from the start of the flush if that was before
     * @throws MoneroError if the listener finishes no notification in the milliseconds, which leaves the wallet done and the listener
     * behind, or the thread is interrupted while it waits for the listener
     */
    fun flush(idleTimeoutMs: Long = FLUSH_IDLE_TIMEOUT_MS) {
        if (Thread.currentThread() === dispatcher) return
        val flush = Flush(CountDownLatch(1))
        val idleTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(idleTimeoutMs)
        val start = System.nanoTime()
        queue.offer(flush)
        try {
            while (true) {
                val lastProgress = lastProgressNanos
                val idleSince = if (lastProgress - start > 0) lastProgress else start
                val remaining = idleTimeoutNanos - (System.nanoTime() - idleSince)
                if (remaining <= 0) {
                    throw MoneroError(
                        "The wallet is done, but the listener finished no notification in $idleTimeoutMs ms: it never returns or takes too long"
                    )
                }
                if (flush.done.await(remaining, TimeUnit.NANOSECONDS)) return
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw MoneroError("Interrupted while waiting for the listener of the wallet", e)
        }
    }

    /**
     * Stop the thread of the listener, after the notifications that wait, and free the native listener. The listener has to be removed
     * from the wallet first, or the wallet has to be closed.
     */
    override fun close() {
        queue.offer(STOP)
        if (Thread.currentThread() !== dispatcher) dispatcher.join(JOIN_TIMEOUT_MS)
        MoneroNative.lib.monero_wallet_listener_free(handle)
    }

    private class SyncProgress(val height: Long, val startHeight: Long, val endHeight: Long, val percentDone: Double, val message: String?)

    private class NewBlock(val height: Long)

    private class BalancesChanged(val balance: Long, val unlockedBalance: Long)

    private class OutputReceived(val json: String)

    private class OutputSpent(val json: String)

    private class Flush(val done: CountDownLatch)

    companion object {
        private val STOP = Any()
        private const val JOIN_TIMEOUT_MS = 5000L

        // A listener that finishes no notification in a minute is not a slow one, it is stuck: it never returns from the one it has, or it waits
        // on a lock of the thread that flushed. A slow one that finishes them one after the other is waited for
        private const val FLUSH_IDLE_TIMEOUT_MS = 60_000L
    }
}
