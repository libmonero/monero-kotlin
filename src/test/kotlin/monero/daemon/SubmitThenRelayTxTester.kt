package monero.daemon

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.daemon.model.MoneroTx

/**
 * Tests that the daemon takes transactions without relaying them, and relays them later, like SubmitThenRelayTxTester of monero-python.
 *
 * @param daemon is the daemon that takes the transactions
 * @param txs are the transactions to submit, which the wallet made and did not relay
 */
class SubmitThenRelayTxTester(private val daemon: MoneroDaemon, private val txs: List<MoneroTx>) {

    /**
     * Check if a transaction is in the pool of the daemon, where it is not relayed yet.
     */
    fun isTxInPool(txHash: String): Boolean {
        for (aTx in daemon.getTxPool()) {
            if (aTx.hash == txHash) {
                assertEquals(false, aTx.isRelayed)
                return true
            }
        }
        return false
    }

    /**
     * Check that the transactions are in the pool of the daemon and are relayed.
     */
    fun ensureTxsRelayed() {
        val poolTxs = daemon.getTxPool()
        for (tx in txs) {
            var found = false
            for (aTx in poolTxs) {
                if (aTx.hash == tx.hash) {
                    assertEquals(true, aTx.isRelayed)
                    found = true
                    break
                }
            }

            assertTrue(found, "Tx was not found after being submitted to the daemon's tx pool")
        }
    }

    /**
     * Submit the transactions without relaying them, and check that the pool has them and the daemon says that they are not relayed.
     *
     * @return the hashes of the transactions
     */
    fun getAndTestTxHashes(): List<String> {
        val txHashes = ArrayList<String>()
        for (tx in txs) {
            val txHash = assertNotNull(tx.hash)
            val fullHex = assertNotNull(tx.fullHex)
            txHashes.add(txHash)
            val result = daemon.submitTxHex(fullHex, true)
            DaemonAssertions.testSubmitTxResultGood(result)
            assertEquals(false, result.isRelayed)

            // ensure tx is in pool
            assertTrue(isTxInPool(txHash), "Tx was not found after being submitted to the daemon's tx pool")

            // fetch tx by hash and ensure not relayed
            val fetchedTx = assertNotNull(daemon.getTx(txHash))
            assertEquals(false, fetchedTx.isRelayed)
        }
        return txHashes
    }

    /**
     * Submit the transactions, relay them, and check that the pool has them as relayed.
     */
    fun test() {
        // submit txs hex but don't relay
        val txHashes = getAndTestTxHashes()

        // relay the txs
        try {
            if (txHashes.size == 1) daemon.relayTxByHash(txHashes[0]) else daemon.relayTxsByHash(txHashes)
        } catch (e: Exception) {
            // flush txs when relay fails to prevent double spends in other tests
            daemon.flushTxPool(txHashes)
            throw e
        }

        // wait for txs to be relayed
        // TODO (monero-project): all txs should be relayed: https://github.com/monero-project/monero/issues/8523
        Thread.sleep(1000)
        ensureTxsRelayed()
    }
}
