package monero.internal

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

    // A listener that takes a number of notifications slowly, one after the other, and then never returns from the next one until it is released
    private class SlowListener(private val taking: Int, private val delayMs: Long) : MoneroWalletListener() {
        val taken = AtomicInteger()
        val release = CountDownLatch(1)

        override fun onNewBlock(height: Long) {
            if (taken.getAndIncrement() < taking) Thread.sleep(delayMs) else release.await()
        }
    }

    // The callbacks of the native listener only queue what monero-c tells them, so a test tells them what monero-c would
    private fun WalletEvents.notifyNewBlock(height: Long) {
        val callback = WalletEvents::class.java.getDeclaredField("onNewBlock").apply { isAccessible = true }.get(this)
        (callback as WalletNewBlockCallback).invoke(null, height)
    }

    private fun timeoutMessage(idleTimeoutMs: Long) =
        "The wallet is done, but the listener finished no notification in $idleTimeoutMs ms: it never returns or takes too long"

    private fun millisSince(start: Long) = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

    @Test
    fun testFlushWaitsForNothingWhenTheListenerIsIdle() {
        WalletEvents(TakingListener()).use { events ->
            events.flush(200)
        }
    }

    // A listener that is slow but finishes one notification after the other is waited for, though the queue takes it longer than the timeout
    @Test
    fun testFlushOfASlowListenerThatKeepsFinishingNotificationsWaitsForAllOfThem() {
        val listener = SlowListener(taking = 10, delayMs = 100)
        WalletEvents(listener).use { events ->
            repeat(10) { events.notifyNewBlock(it.toLong()) }

            val start = System.nanoTime()
            events.flush(600)
            assertTrue(millisSince(start) > 600, "the notifications did not take longer than the timeout")
            assertEquals(10, listener.taken.get())
        }
    }

    // A listener that gets stuck on a notification holds the flush for the timeout, counted from the last notification that it finished
    @Test
    fun testFlushOfAListenerThatGetsStuckFailsAfterTheTimeoutFromTheLastNotification() {
        val listener = SlowListener(taking = 3, delayMs = 100)
        WalletEvents(listener).use { events ->
            try {
                repeat(4) { events.notifyNewBlock(it.toLong()) }

                val start = System.nanoTime()
                val error = assertThrows(MoneroError::class.java) { events.flush(600) }
                assertEquals(timeoutMessage(600), error.message)
                // the third notification is finished 300 ms after the first one starts, and the timeout runs from there
                assertTrue(millisSince(start) >= 850, "the flush gave up before the timeout from the last notification")
            } finally {
                listener.release.countDown()
            }
        }
    }

    // A listener that never returns from a notification holds the flush for the timeout, and no longer: the flush has to give up instead of
    // holding the sync that called it
    @Test
    fun testFlushOfAListenerThatNeverReturnsFailsAfterTheTimeout() {
        val listener = SlowListener(taking = 0, delayMs = 0)
        WalletEvents(listener).use { events ->
            try {
                events.notifyNewBlock(1)

                val start = System.nanoTime()
                val error = assertThrows(MoneroError::class.java) { events.flush(200) }
                assertEquals(timeoutMessage(200), error.message)
                assertTrue(millisSince(start) >= 200, "the flush returned before the timeout")
            } finally {
                listener.release.countDown()
            }
        }
    }

    // The wait of the flush is the wait of a sync: an interrupt of the thread that waits says so and keeps the flag
    @Test
    fun testFlushOfAListenerThatNeverReturnsFailsWhenInterrupted() {
        val listener = SlowListener(taking = 0, delayMs = 0)
        WalletEvents(listener).use { events ->
            try {
                events.notifyNewBlock(1)

                val finished = CountDownLatch(1)
                val error = arrayOfNulls<MoneroError>(1)
                val waiting = Thread {
                    try {
                        events.flush(5000)
                    } catch (e: MoneroError) {
                        error[0] = e
                    } finally {
                        finished.countDown()
                    }
                }
                waiting.start()
                waiting.interrupt()
                assertTrue(finished.await(5, TimeUnit.SECONDS), "the flush never returned")
                assertEquals("Interrupted while waiting for the listener of the wallet", error[0]!!.message)
                assertTrue(waiting.isInterrupted, "the interrupt was taken and not kept")
            } finally {
                listener.release.countDown()
            }
        }
    }

    // The thread of a listener that was removed took all it had before it stopped, and takes nothing after, so a flush that comes then, from a
    // sync that goes on while another thread removes the listener, has nothing to wait for
    @Test
    fun testFlushOfAClosedEventsReturnsAtOnce() {
        val events = WalletEvents(TakingListener())
        events.close()

        val start = System.nanoTime()
        events.flush(5000)
        assertTrue(millisSince(start) < 1000, "the flush waited for events that were closed")
    }
}
