package monero.daemon

import java.util.Collections
import monero.TestLog
import monero.daemon.model.MoneroBlockHeader
import monero.daemon.model.MoneroDaemonListener

/**
 * Collects the block headers that a daemon notifies, like DaemonNotificationCollector of monero-python. A notification runs on a thread of
 * the daemon, where an assertion that fails would be lost, so the checks of the collector are kept in [errors] for the test to look at.
 *
 * @param daemon is the daemon whose headers are collected
 * @param autoRemove is whether the listener removes itself from the daemon after the first header
 */
class DaemonNotificationCollector(private val daemon: MoneroDaemon, private val autoRemove: Boolean = false) : MoneroDaemonListener() {

    private val logger = TestLog.logger("DaemonNotificationCollector")

    val blockHeaders: MutableList<MoneroBlockHeader> = Collections.synchronizedList(ArrayList())
    val blockHashes: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet())
    val errors: MutableList<Throwable> = Collections.synchronizedList(ArrayList())

    @Volatile
    var lastBlockHeight: Long? = null
        private set

    val numBlockHeaders: Int get() = blockHeaders.size
    val numBlockHashes: Int get() = blockHashes.size

    override fun onBlockHeader(header: MoneroBlockHeader) {
        try {
            val hash = checkNotNull(header.hash)
            check(blockHashes.add(hash)) { "Duplicate block header found: $hash" }
            blockHeaders.add(header)

            val height = checkNotNull(header.height)
            val last = lastBlockHeight
            if (last != null && height != last + 1) logger.warning("Daemon listener missed blocks range ${last + 1}-${height - 1}")
            lastBlockHeight = height

            if (autoRemove) daemon.removeListener(this)
        } catch (e: Throwable) {
            logger.severe("$e")
            errors.add(e)
        }
    }
}
