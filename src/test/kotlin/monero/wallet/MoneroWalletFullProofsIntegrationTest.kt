package monero.wallet

import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import monero.common.MoneroError
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroTxWallet
import org.junit.jupiter.api.Tag

/**
 * The keys and the proofs of the transactions of a full wallet, against a regtest node, like the tests of monero-python
 * (tests/test_monero_wallet_common.py) that check a transaction with its key, with a proof of the payment and with a proof of the spend.
 * They read the wallet of TxHistoryWallet, which has transactions to an address that is not its own. They need monero-c and the regtest
 * node of src/test/docker.
 */
@Tag("integration")
class MoneroWalletFullProofsIntegrationTest {

    // the most transactions that a test proves, as WalletUtils.MAX_TX_PROOFS of monero-python
    private val maxTxProofs = 25

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    private fun wallet(): MoneroWalletFull = TxHistoryWallet.get()

    private fun withDestinations(confirmed: Boolean?): List<MoneroTxWallet> {
        val query = MoneroTxQuery()
        query.isConfirmed = confirmed
        query.transferQuery = MoneroTransferQuery().also { it.hasDestinations = true }
        return wallet().getTxs(query).take(maxTxProofs)
    }

    // What an address received in a transaction is what it was paid, unless the address is one that the change goes to as well: the transaction
    // that pays the primary address of the wallet, or its integrated address, receives the change too
    private fun assertReceived(address: String, paid: BigInteger, received: BigInteger) {
        if (address == RegtestSupport.MINING_ADDRESS) assertEquals(paid, received, "what $address received")
        else assertTrue(received >= paid, "what $address received: $received of $paid")
    }

    // Can check a transfer using the secret key of the transaction and the destination
    @Test
    fun testCheckTxKey() {
        val wallet = wallet()
        val txs = withDestinations(true)
        assertTrue(txs.size > 1, "No transactions found with outgoing destinations")

        for (tx in txs) {
            val key = wallet.getTxKey(tx.hash!!)
            // the key of the transaction, followed by the additional keys that a payment to subaddresses has
            assertTrue(key.matches(Regex("([0-9a-f]{64})+")), "the key of a transaction: $key")
            assertTrue(tx.outgoingTransfer!!.destinations!!.isNotEmpty())
            for (destination in tx.outgoingTransfer!!.destinations!!) {
                val check = wallet.checkTxKey(tx.hash!!, key, destination.address!!)
                TxAssertions.validateCheckTx(check)
                assertEquals(true, check.isGood)
                assertReceived(destination.address!!, destination.amount!!, check.receivedAmount!!)
            }
        }

        // a key, a hash and an address that are not valid
        val tx = txs.first()
        val key = wallet.getTxKey(tx.hash!!)
        val destination = tx.outgoingTransfer!!.destinations!!.first()
        assertEquals("TX hash has invalid format", assertFailsWith<MoneroError> { wallet.getTxKey("invalid_tx_id") }.message)
        assertEquals("TX hash has invalid format", assertFailsWith<MoneroError> { wallet.checkTxKey("invalid_tx_id", key, destination.address!!) }.message)
        assertEquals("Tx key has invalid format", assertFailsWith<MoneroError> { wallet.checkTxKey(tx.hash!!, "invalid_tx_key", destination.address!!) }.message)
        assertEquals("Invalid address", assertFailsWith<MoneroError> { wallet.checkTxKey(tx.hash!!, key, "invalid_tx_address") }.message)

        // an address that the transaction did not pay: the check is good and nothing was received
        val other = wallet.getTxs().flatMap { it.outgoingTransfer?.destinations ?: emptyList() }.map { it.address!! }.first { it != destination.address }
        val different = wallet.checkTxKey(tx.hash!!, key, other)
        TxAssertions.validateCheckTx(different)
        assertEquals(true, different.isGood)
        assertTrue(different.receivedAmount!!.signum() >= 0)
    }

    // The key of a transaction that the wallet did not send is not one it has, and the key of another transaction proves no payment
    @Test
    fun testCheckTxKeyThatIsNotTheOne() {
        val wallet = wallet()
        val sent = withDestinations(true)
        val received = wallet.getTxs().first { it.isOutgoing != true }
        assertFailsWith<MoneroError> { wallet.getTxKey(received.hash!!) }
        assertFailsWith<MoneroError> { wallet.getTxKey("a".repeat(64)) }

        val tx = sent[0]
        val address = tx.outgoingTransfer!!.destinations!!.first().address!!
        val keyOfAnother = wallet.getTxKey(sent[1].hash!!)
        val check = wallet.checkTxKey(tx.hash!!, keyOfAnother, address)
        TxAssertions.validateCheckTx(check)
        // wallet2 checks any key: the answer is good, and the address received nothing with it
        assertEquals(true, check.isGood)
        assertEquals(BigInteger.ZERO, check.receivedAmount, "the key of another transaction proves no payment")
    }

    // Can prove a transaction by getting its signature
    @Test
    fun testCheckTxProof() {
        val wallet = wallet()
        val txs = withDestinations(null)
        assertTrue(txs.size > 1, "No transactions found with outgoing destinations")
        val message = "This transaction definitely happened."

        for (tx in txs) {
            for (destination in tx.outgoingTransfer!!.destinations!!) {
                val signature = wallet.getTxProof(tx.hash!!, destination.address!!, message)
                val check = wallet.checkTxProof(tx.hash!!, destination.address!!, message, signature)
                TxAssertions.validateCheckTx(check)
                assertEquals(true, check.isGood)
                assertReceived(destination.address!!, destination.amount!!, check.receivedAmount!!)
            }
        }

        // without a message
        val tx = txs[0]
        val destination = tx.outgoingTransfer!!.destinations!!.first()
        val signature = wallet.getTxProof(tx.hash!!, destination.address!!)
        val check = wallet.checkTxProof(tx.hash!!, destination.address!!, "", signature)
        TxAssertions.validateCheckTx(check)
        assertEquals(true, check.isGood)
        assertEquals(true, wallet.checkTxProof(tx.hash!!, destination.address!!, null, signature).isGood)

        // a hash and an address that are not valid
        assertEquals("TX hash has invalid format", assertFailsWith<MoneroError> { wallet.getTxProof("invalid_tx_id", destination.address!!) }.message)
        assertEquals("TX hash has invalid format", assertFailsWith<MoneroError> { wallet.checkTxProof("invalid_tx_id", destination.address!!, "", signature) }.message)
        assertEquals("Invalid address", assertFailsWith<MoneroError> { wallet.checkTxProof(tx.hash!!, "invalid_tx_address", "", signature) }.message)

        // the wrong message
        val rightMessage = wallet.getTxProof(tx.hash!!, destination.address!!, "This is the right message")
        val wrongMessage = wallet.checkTxProof(tx.hash!!, destination.address!!, "This is the wrong message", rightMessage)
        assertEquals(false, wrongMessage.isGood)
        TxAssertions.validateCheckTx(wrongMessage)
    }

    // A signature of another transaction, or none, is an error
    @Test
    fun testCheckTxProofWithASignatureThatIsNotTheOne() {
        val wallet = wallet()
        val txs = withDestinations(null)
        val tx = txs[0]
        val destination = tx.outgoingTransfer!!.destinations!!.first()
        val other = txs[1]
        val wrong = wallet.getTxProof(other.hash!!, other.outgoingTransfer!!.destinations!!.first().address!!, "This is the right message")
        val mismatch = assertFailsWith<MoneroError> { wallet.checkTxProof(tx.hash!!, destination.address!!, "This is the right message", wrong) }
        assertEquals("Signature size mismatch with additional tx pubkeys", mismatch.message)

        val empty = assertFailsWith<MoneroError> { wallet.checkTxProof(tx.hash!!, destination.address!!, "This is the right message", "") }
        assertEquals("Must provide signature to check tx proof", empty.message)
    }

    // Can prove a spend using a signature and no destination address
    @Test
    fun testCheckSpendProof() {
        val wallet = wallet()
        val query = MoneroTxQuery()
        query.isIncoming = false
        query.inTxPool = false
        query.isFailed = false
        val txs = wallet.getTxs(query).take(maxTxProofs)
        assertTrue(txs.size > 1, "No confirmed outgoing transactions to test")
        for (tx in txs) {
            assertEquals(true, tx.isConfirmed)
            assertTrue((tx.incomingTransfers ?: emptyList()).isEmpty())
            assertNotNull(tx.outgoingTransfer)
        }

        for (tx in txs) {
            val signature = wallet.getSpendProof(tx.hash!!, "I am a message.")
            assertTrue(wallet.checkSpendProof(tx.hash!!, "I am a message.", signature))
        }

        // without a message
        val tx = txs[0]
        val signature = wallet.getSpendProof(tx.hash!!)
        assertTrue(wallet.checkSpendProof(tx.hash!!, "", signature))
        assertTrue(wallet.checkSpendProof(tx.hash!!, null, signature))

        // a hash that is not valid
        assertEquals("TX hash has invalid format", assertFailsWith<MoneroError> { wallet.getSpendProof("invalid_tx_id") }.message)
        assertEquals("TX hash has invalid format", assertFailsWith<MoneroError> { wallet.checkSpendProof("invalid_tx_id", "", signature) }.message)

        // the wrong message, and the signature of another transaction
        val right = wallet.getSpendProof(tx.hash!!, "This is the right message")
        assertFalse(wallet.checkSpendProof(tx.hash!!, "This is the wrong message", right))
        val ofAnother = wallet.getSpendProof(txs[1].hash!!, "This is the right message")
        assertFalse(wallet.checkSpendProof(tx.hash!!, "This is the right message", ofAnother))
    }
}
