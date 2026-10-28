package monero.internal

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import monero.common.MoneroError

/**
 * One thread for the calls of a daemon or a wallet, which run one after the other.
 *
 * The stack is 8 MB, as the main thread of Linux: the default of a thread of the JVM is 1 MB, and monero-cpp parses
 * JSON by recursion.
 */
internal class NativeWorker(name: String) : AutoCloseable {

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(null, task, name, STACK_SIZE).apply { isDaemon = true }
    }

    /**
     * Run a call on the thread of the worker, and wait for its result. An error of the call is thrown as it is.
     */
    fun <T> call(task: () -> T): T {
        val future = try {
            executor.submit(Callable { task() })
        } catch (e: RejectedExecutionException) {
            throw MoneroError("The daemon is closed", e)
        }
        try {
            return future.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } catch (e: InterruptedException) {
            future.cancel(false)
            Thread.currentThread().interrupt()
            throw MoneroError("Interrupted while waiting for monero-c", e)
        }
    }

    /**
     * Stop the thread after the calls that are in the queue. A call that is queued still runs, since the caller
     * frees the handle right after the close, and a call on a freed handle is a second thread on the handle, which
     * the C ABI does not allow (monero_c_wallet.h:49-53).
     */
    override fun close() {
        executor.shutdown()
        while (!executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
            // the wait is unbounded: the calls of a worker are finite, and freeing the handle under one is not
        }
    }

    companion object {
        private const val STACK_SIZE = 8L * 1024 * 1024
    }
}
