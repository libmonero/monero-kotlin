package monero.internal

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import monero.common.MoneroError
import monero.wallet.model.MoneroWalletListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class WalletEventsTest {

    // A listener that takes every notification, which the dispatcher of the events calls one after the other
    private class TakingListener : MoneroWalletListener() {
        val notifications = CountDownLatch(1)

        override fun onNewBlock(height: Long) {
            notifications.countDown()
        }
    }

    @Test
    fun flush_WaitsForNothingWhenTheListenerIsIdle() {
        WalletEvents(TakingListener()).use { events ->
            events.flush(200)
        }
    }

    // The dispatcher of a closed events takes nothing off the queue anymore, which is what a listener that never
    // returns holds the flush of an open one in: the flush has to give up instead of holding the sync that called it
    @Test
    fun flush_OfAListenerThatTakesNothing_FailsAfterTheTimeout() {
        val events = WalletEvents(TakingListener())
        events.close()

        val start = System.nanoTime()
        val error = assertThrows(MoneroError::class.java) { events.flush(200) }
        assertEquals("the listener did not take the notifications in 200ms, it is slow or it never returns", error.message)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 200, "the flush returned before the timeout")
    }

    // The wait of the flush is the wait of a sync: an interrupt of the thread that waits says so and keeps the flag
    @Test
    fun flush_OfAListenerThatTakesNothing_FailsWhenInterrupted() {
        val events = WalletEvents(TakingListener())
        events.close()

        val interrupted = CountDownLatch(1)
        val error = arrayOfNulls<MoneroError>(1)
        val waiting = Thread {
            try {
                events.flush(5000)
            } catch (e: MoneroError) {
                error[0] = e
            } finally {
                interrupted.countDown()
            }
        }
        waiting.start()
        waiting.interrupt()
        assertTrue(interrupted.await(5, TimeUnit.SECONDS), "the flush never returned")
        assertEquals("the wait for the listener of the wallet was interrupted", error[0]!!.message)
        assertTrue(waiting.isInterrupted, "the interrupt was taken and not kept")
    }
}
