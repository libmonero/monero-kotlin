package monero.internal

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import monero.common.MoneroError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeWorkerTest {

    @Test
    fun call_ReturnsTheResultOfTheTask() {
        NativeWorker("test").use { worker ->
            assertEquals(42, worker.call { 42 })
        }
    }

    @Test
    fun call_ThrowsTheErrorOfTheTaskAsItIs() {
        NativeWorker("test").use { worker ->
            val error = assertThrows(MoneroError::class.java) { worker.call { throw MoneroError("it failed") } }
            assertEquals("it failed", error.message)
        }
    }

    @Test
    fun call_OfAClosedWorker_Fails() {
        val worker = NativeWorker("test")
        worker.close()

        assertThrows(MoneroError::class.java) { worker.call { 1 } }
    }

    // The owner of a worker frees its handle as soon as the close returns
    @Test
    fun close_WaitsForTheCallThatIsRunning() {
        val worker = NativeWorker("test")
        val started = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        val call = CompletableFuture.runAsync {
            worker.call {
                started.countDown()
                Thread.sleep(300)
                finished.set(true)
            }
        }
        assertTrue(started.await(10, TimeUnit.SECONDS))

        worker.close()

        assertTrue(finished.get(), "the close returned while a call was still running")
        call.get(10, TimeUnit.SECONDS)
    }

    @Test
    fun close_WaitsForTheCallsInTheQueue() {
        val worker = NativeWorker("test")
        val started = CountDownLatch(1)
        val queuedRan = AtomicBoolean(false)
        val running = CompletableFuture.runAsync {
            worker.call {
                started.countDown()
                Thread.sleep(300)
            }
        }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        val queued = CompletableFuture.runAsync { worker.call { queuedRan.set(true) } }

        // the call is queued behind the running one when the close starts
        Thread.sleep(100)
        assertFalse(queuedRan.get())
        worker.close()

        assertTrue(queuedRan.get(), "the close returned before a call that was queued ran")
        running.get(10, TimeUnit.SECONDS)
        queued.get(10, TimeUnit.SECONDS)
    }
}
