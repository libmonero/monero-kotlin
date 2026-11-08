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
        NativeWorker("test", "The test is closed").use { worker ->
            assertEquals(42, worker.call { 42 })
        }
    }

    @Test
    fun call_ThrowsTheErrorOfTheTaskAsItIs() {
        NativeWorker("test", "The test is closed").use { worker ->
            val error = assertThrows(MoneroError::class.java) { worker.call { throw MoneroError("it failed") } }
            assertEquals("it failed", error.message)
        }
    }

    // a close that an interrupt ends half way leaves the handle of the owner freed under a queued call, so the
    // close waits the interrupt out: the owner frees the handle only after the last call has ended
    @Test
    fun close_WaitsAnInterruptOut() {
        val worker = NativeWorker("test", "The test is closed")
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val call = CompletableFuture.runAsync {
            worker.call {
                started.countDown()
                Thread.sleep(1000)
                finished.countDown()
            }
        }
        started.await()

        val closer = Thread {
            worker.close()
        }
        closer.start()
        Thread.sleep(100)   // the closer is in the wait by now
        closer.interrupt()
        closer.join(10000)
        assertEquals(false, closer.isAlive, "the close did not return")

        // the queued call ended, and the interrupt is still on the thread that closed
        assertEquals(true, finished.await(0, TimeUnit.MILLISECONDS))
        assertEquals(true, closer.isInterrupted)
        call.get(10, TimeUnit.SECONDS)
    }

    // the worker runs one call at a time, so a call that waits on a call of its own worker would wait
    // on itself forever
    @Test
    fun call_FromTheWorkerItself_Fails() {
        NativeWorker("test", "The test is closed").use { worker ->
            val error = assertThrows(MoneroError::class.java) {
                worker.call { worker.call { 1 } }
            }
            assertEquals("A call of a worker can not wait on a call of the same worker", error.message)
        }
    }

    @Test
    fun call_OfAClosedWorker_Fails() {
        val worker = NativeWorker("test", "The test is closed")
        worker.close()

        val error = assertThrows(MoneroError::class.java) { worker.call { 1 } }
        assertEquals("The test is closed", error.message)
    }

    // The owner of a worker frees its handle as soon as the close returns
    @Test
    fun close_WaitsForTheCallThatIsRunning() {
        val worker = NativeWorker("test", "The test is closed")
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
        val worker = NativeWorker("test", "The test is closed")
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
