package monero.wallet

import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.wallet.model.MoneroTxWallet

/**
 * What the integration tests check in the transactions that a wallet makes, like the tests of the transactions of monero-python.
 */
object TxAssertions {

    // 7500000 * 10000 atomic units: a fee that no transaction of the regtest chain goes over, as TxWalletUtils.MAX_FEE of monero-python
    val MAX_FEE: BigInteger = BigInteger.valueOf(75_000_000_000L)

    /**
     * Check a transaction that the wallet made: it has a hash, a fee, an outgoing transfer with its destinations and the metadata to
     * relay it later, it is not confirmed, and it is in the pool if it was relayed.
     */
    fun validateSent(tx: MoneroTxWallet, relayed: Boolean) {
        assertTrue(tx.hash!!.matches(Regex("[0-9a-f]{64}")), "hash of the transaction: ${tx.hash}")
        assertTrue(tx.fee!! > BigInteger.ZERO)
        assertEquals(true, tx.isOutgoing)
        assertEquals(false, tx.isConfirmed)
        assertNotNull(tx.outgoingTransfer)
        assertTrue(tx.outgoingTransfer!!.destinations!!.isNotEmpty())
        assertNotNull(tx.metadata, "the metadata of the transaction relays it later")
        if (relayed) {
            assertEquals(true, tx.isRelayed)
            assertEquals(true, tx.inTxPool)
        } else {
            assertEquals(false, tx.isRelayed)
            assertEquals(false, tx.inTxPool)
        }
    }
}
