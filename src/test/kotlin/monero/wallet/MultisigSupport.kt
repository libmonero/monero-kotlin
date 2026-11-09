package monero.wallet

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.wallet.model.MoneroWalletConfig

/**
 * What the multisig tests share: the participants of a multisig wallet and the key setup that makes them one, which
 * needs no daemon.
 */
object MultisigSupport {

    class Setup<W : MoneroWalletNative>(val participants: List<W>, val address: String) : AutoCloseable {
        override fun close() {
            for (participant in participants) participant.close()
        }
    }

    /**
     * Create n wallets in memory and make them the participants of an m of n multisig wallet, the way
     * MultisigSampleCodeTester of monero-python does: each one prepares its hex, makes its wallet multisig from the
     * hexes of the others, and the participants exchange keys n - m + 1 times, the last of which returns the address.
     *
     * @param m is the number of signatures that a transfer needs
     * @param n is the number of participants
     * @param newConfig makes the config of a participant
     * @param password is the password of the participants, which the key setup asks for again
     * @param prepare is called on each participant as it is created, for the daemon connection of a test that spends
     * @return the participants, final and with the same address
     */
    fun create(
        m: Int,
        n: Int,
        newConfig: () -> MoneroWalletConfig,
        password: String = "",
        prepare: (MoneroWalletFull) -> Unit = {}
    ): Setup<MoneroWalletFull> {
        return setup(m, n, { MoneroWalletFull.createWallet(newConfig()).also(prepare) }, password)
    }

    /**
     * Make n wallets that a function creates the participants of an m of n multisig wallet, in the way of [create]. They can be wallets of a
     * server, which the password that they were made with has to be given for.
     *
     * @param m is the number of signatures that a transfer needs
     * @param n is the number of participants
     * @param newWallet creates a participant
     * @param password is the password of the participants, which the key setup asks for again
     * @return the participants, final and with the same address
     */
    fun <W : MoneroWalletNative> setup(m: Int, n: Int, newWallet: () -> W, password: String = ""): Setup<W> {
        val participants = (0 until n).map { newWallet() }
        try {
            val prepared = participants.map { it.prepareMultisig() }
            var hexes = participants.mapIndexed { i, wallet ->
                wallet.makeMultisig(prepared.filterIndexed { j, _ -> j != i }, m, password)
            }

            var address: String? = null
            repeat(n - m + 1) { round ->
                val nextHexes = mutableListOf<String>()
                for (i in participants.indices) {
                    val result = participants[i].exchangeMultisigKeys(hexes.filterIndexed { j, _ -> j != i }, password)
                    val resultHex = assertNotNull(result.multisigHex, "the exchange round returned no hex")
                    assertTrue(resultHex.isNotEmpty())
                    if (round == n - m) {
                        val resultAddress = assertNotNull(result.address, "the last exchange round returned no address")
                        if (address == null) address = resultAddress else assertEquals(address, resultAddress)
                    } else {
                        nextHexes.add(resultHex)
                    }
                }
                hexes = nextHexes
            }
            return Setup(participants, assertNotNull(address))
        } catch (e: Throwable) {
            for (participant in participants) participant.close()
            throw e
        }
    }
}
