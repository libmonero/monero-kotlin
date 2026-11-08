package monero.wallet

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertFailsWith
import monero.wallet.model.MoneroAccount
import monero.wallet.model.MoneroBlockWallet
import monero.wallet.model.MoneroIncomingTransfer
import monero.wallet.model.MoneroSubaddress
import monero.wallet.model.MoneroTxWallet
import org.junit.jupiter.api.Tag

/**
 * The comparison of two wallets by what the chain says, which the tests that compare a wallet with a ground truth one use: it has to accept
 * two wallets that have the same on-chain data, whatever only a wallet knows, and tell them apart when they differ, at the subaddresses
 * too, which the utilities of monero-python do not look at.
 */
@Tag("unit")
class WalletEqualityTest {

    private fun subaddress(index: Int, balance: Long = 0, used: Boolean = false, label: String? = null) = MoneroSubaddress().also {
        it.accountIndex = 0
        it.index = index
        it.address = "address-$index"
        it.label = label
        it.balance = BigInteger.valueOf(balance)
        it.unlockedBalance = BigInteger.valueOf(balance)
        it.isUsed = used
    }

    private fun account(index: Int = 0, balance: Long = 0, tag: String? = null, vararg subaddresses: MoneroSubaddress) = MoneroAccount().also {
        it.index = index
        it.primaryAddress = "primary-$index"
        it.balance = BigInteger.valueOf(balance)
        it.unlockedBalance = BigInteger.valueOf(balance)
        it.tag = tag
        it.subaddresses = subaddresses.toList()
    }

    private fun tx(hash: String, height: Long, fee: Long = 10, note: String? = null, inPool: Boolean = false) = MoneroTxWallet().also {
        it.hash = hash
        it.fee = BigInteger.valueOf(fee)
        it.note = note
        it.inTxPool = inPool
        it.isConfirmed = !inPool
        it.isFailed = false
        if (!inPool) {
            val block = MoneroBlockWallet()
            block.height = height
            block.txs = listOf(it)
            it.block = block
        }
    }

    private fun transfer(tx: MoneroTxWallet, amount: Long) = MoneroIncomingTransfer().also {
        it.tx = tx
        it.amount = BigInteger.valueOf(amount)
        it.accountIndex = 0
        it.subaddressIndex = 0
        it.address = "address-${tx.hash}"
    }

    // ----------------------------------- accounts -----------------------------------

    @Test
    fun assertAccountsEqual_AcceptsTheSameAccountsWithoutLookingAtTagsAndLabels() {
        WalletEquality.assertAccountsEqual(
            listOf(account(0, 5, "savings", subaddress(0, 5, used = true, label = "primary"))),
            listOf(account(0, 5, null, subaddress(0, 5, used = true, label = "other label")))
        )
    }

    @Test
    fun assertAccountsEqual_ComparesTheSubaddresses() {
        assertFailsWith<AssertionError> {
            WalletEquality.assertAccountsEqual(listOf(account(0, 5, null, subaddress(0, 5))), listOf(account(0, 5, null, subaddress(0, 4))))
        }
    }

    @Test
    fun assertAccountsEqual_ComparesTheBalances() {
        assertFailsWith<AssertionError> { WalletEquality.assertAccountsEqual(listOf(account(0, 5)), listOf(account(0, 6))) }
    }

    @Test
    fun assertAccountsEqual_AcceptsAnAccountPastTheEndThatIsUnused() {
        val unused = account(1, 0, null, subaddress(0))
        WalletEquality.assertAccountsEqual(listOf(account(0, 5)), listOf(account(0, 5), unused))
        WalletEquality.assertAccountsEqual(listOf(account(0, 5), unused), listOf(account(0, 5)))
    }

    @Test
    fun assertAccountsEqual_RejectsAnAccountPastTheEndThatHasFunds() {
        assertFailsWith<AssertionError> { WalletEquality.assertAccountsEqual(listOf(account(0, 5)), listOf(account(0, 5), account(1, 3, null, subaddress(0, 3, used = true)))) }
    }

    @Test
    fun assertAccountsEqual_AcceptsSubaddressesPastTheEndThatAreUnusedAndRejectsUsedOnes() {
        val first = subaddress(0, 5, used = true)
        WalletEquality.assertAccountsEqual(listOf(account(0, 5, null, first)), listOf(account(0, 5, null, subaddress(0, 5, used = true), subaddress(1))))
        WalletEquality.assertAccountsEqual(listOf(account(0, 5, null, subaddress(0, 5, used = true), subaddress(1))), listOf(account(0, 5, null, subaddress(0, 5, used = true))))
        assertFailsWith<AssertionError> {
            WalletEquality.assertAccountsEqual(listOf(account(0, 5, null, subaddress(0, 5, used = true), subaddress(1, 0, used = true))), listOf(account(0, 5, null, subaddress(0, 5, used = true))))
        }
    }

    // ----------------------------------- transactions -----------------------------------

    @Test
    fun assertTxsEqual_AcceptsTheSameTxsWithoutLookingAtNotesAndTheTxsOfThePool() {
        WalletEquality.assertTxsEqual(
            listOf(tx("a", 10, note = "a note"), tx("b", 11), tx("pool", 0, inPool = true)),
            listOf(tx("a", 10), tx("b", 11))
        )
    }

    @Test
    fun assertTxsEqual_ComparesTheFields() {
        assertFailsWith<AssertionError> { WalletEquality.assertTxsEqual(listOf(tx("a", 10, fee = 10)), listOf(tx("a", 10, fee = 11))) }
    }

    @Test
    fun assertTxsEqual_RejectsATxThatTheOtherWalletDoesNotHave() {
        assertFailsWith<AssertionError> { WalletEquality.assertTxsEqual(listOf(tx("a", 10), tx("b", 11)), listOf(tx("a", 10))) }
        assertFailsWith<AssertionError> { WalletEquality.assertTxsEqual(listOf(tx("a", 10)), listOf(tx("b", 10))) }
    }

    @Test
    fun assertTxsEqual_RejectsATxInAnotherBlock() {
        assertFailsWith<AssertionError> { WalletEquality.assertTxsEqual(listOf(tx("a", 10)), listOf(tx("a", 12))) }
    }

    // ----------------------------------- transfers -----------------------------------

    @Test
    fun assertTransfersEqual_AcceptsTheSameTransfersWithoutLookingAtTheAddresses() {
        val a1 = tx("a", 10)
        val a2 = tx("a", 10)
        WalletEquality.assertTransfersEqual(listOf(transfer(a1, 5), transfer(tx("b", 11), 7)), listOf(transfer(a2, 5).also { it.address = "another" }, transfer(tx("b", 11), 7)))
    }

    @Test
    fun assertTransfersEqual_ComparesTheAmounts() {
        assertFailsWith<AssertionError> { WalletEquality.assertTransfersEqual(listOf(transfer(tx("a", 10), 5)), listOf(transfer(tx("a", 10), 6))) }
    }

    @Test
    fun assertTransfersEqual_RejectsTransfersThatAreNotInAscendingOrderByHeight() {
        val first = listOf(transfer(tx("a", 11), 5), transfer(tx("b", 10), 7))
        val second = listOf(transfer(tx("a", 11), 5), transfer(tx("b", 10), 7))
        assertFailsWith<AssertionError> { WalletEquality.assertTransfersEqual(first, second) }
    }

    @Test
    fun assertTransfersEqual_RejectsTransfersOfATxThatAreNotTogether() {
        val a = tx("a", 10)
        val b = tx("b", 10)
        val first = listOf(transfer(a, 1), transfer(b, 2), transfer(a, 3))
        val second = listOf(transfer(tx("a", 10), 1), transfer(tx("b", 10), 2), transfer(tx("a", 10), 3))
        assertFailsWith<AssertionError> { WalletEquality.assertTransfersEqual(first, second) }
    }
}
