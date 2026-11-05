package monero.internal

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import monero.common.MoneroError

/**
 * One thread for the calls of a daemon or a wallet, which run one after the other. A call that reaches the worker after
 * its close fails with the message of the owner, since a wallet and a daemon say that they are closed in their own words.
 *
 * The stack is 8 MB, as the main thread of Linux: the default of a thread of the JVM is 1 MB, and monero-cpp parses
 * JSON by recursion.
 */
internal class NativeWorker(name: String, private val closedMessage: String) : AutoCloseable {

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
            throw MoneroError(closedMessage, e)
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
     * Stop the thread after the calls that are in the queue, and wait until it has run them: the owner frees its handle
     * right after the close, and a call that is still queued would run on a freed handle.
     *
     * @throws MoneroError if the thread that closes is interrupted while it waits
     */
    override fun close() {
        executor.shutdown()
        try {
            // unbounded, since the calls of a worker end and freeing the handle under one of them does not
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw MoneroError("Interrupted while waiting for the calls of monero-c to end", e)
        }
    }

    companion object {
        private const val STACK_SIZE = 8L * 1024 * 1024
    }
}
