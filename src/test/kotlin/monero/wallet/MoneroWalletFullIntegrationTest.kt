package monero.wallet

import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.exists
import kotlin.math.min
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import monero.NativeTestSupport
import monero.common.MoneroError
import monero.common.MoneroRpcConnection
import monero.daemon.model.MoneroNetworkType
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroWalletConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir

/**
 * The full wallet against the monerod of the compose of src/test/integration: wallets are created from a seed, from
 * keys and at random with files in a temporary directory, they connect to the daemon, which a wallet can be at any
 * time after it is created and a create config can as it creates one, and they sync the chain from its first block.
 * The sender then sends to the receiver through the RingCT construction of monero-cpp, and the wallet file proves
 * itself: what a save writes, a close and an open read again with the same balance and the same seed.
 *
 * The wallets are of the regtest network, which monero-cpp has as a flag of the main network, and the blocks are
 * mined the way the send test of the light wallet mines them: a coinbase of a regtest chain spends only sixty blocks
 * after it was mined, and every ring that the wallet builds needs sixteen decoys that spend, so the test mines five
 * blocks to the sender and a hundred and thirty to an address that no wallet watches.
 *
 * It needs the monerod of MONERO_C_TEST_DAEMON_URI, which the compose of src/test/integration provides. The test
 * skips when no daemon answers, like the unit tests skip without monero-c, which this test also needs.
 *
 * The test mines onto the chain of the daemon, so it has to run after MoneroWalletLightIntegrationTest, which
 * asserts the height of a fresh chain: this class is @Order(2) and that one @Order(1), which the class orderer of
 * junit-platform.properties runs in that order.
 */
@Order(2)
@Tag("integration")
class MoneroWalletFullIntegrationTest {

    private val daemonUri = System.getenv("MONERO_C_TEST_DAEMON_URI") ?: "http://127.0.0.1:18081"

    // the test wallet of monero-python's config.ini, whose seed spends
    private val seed = "vortex degrees outbreak teeming gimmick school rounded tonic observant injury leech ought problems ahead upcoming ledge textbook cigar atrium trash dunes eavesdrop dullness evolved vortex"
    private val primaryAddress = "48W9YHwPzRz9aPTeXCA6kmSpW6HsvmWx578jj3of2gT3JwZzwTf33amESBoNDkL6SVK34Q2HTKqgYbGyE1hBws3wCrcBDR2"
    private val privateViewKey = "e8c2288181bad9ec410d7322efd65f663c6da57bd1d1198636278a039743a600"
    private val privateSpendKey = "be7a2f71097f146bdf0fb5bb8edfe2240a9767e15adee74d95af1b5a64f29a0c"

    // the mining wallet of monero-python's config.ini, which receives
    private val receiverSeed = "lush waffle neither siblings when sanity pencil grunt ghost fall rabbits adapt items ornament sequence nitrogen orders fall bagpipe width hope fainted hexagon imagine orders"

    // the password of the wallet files
    private val password = "supersecretpassword123"

    // the amount to send, in atomic units: five monero
    private val amount = BigInteger.valueOf(5000000000000L)

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val connection: MoneroRpcConnection by lazy { MoneroRpcConnection(daemonUri) }

    @TempDir
    lateinit var tempDir: Path

    @BeforeTest
    fun requireLibraryAndDaemon() {
        NativeTestSupport.assumeAvailable()
        assumeTrue(isDaemonUp, "no monerod at $daemonUri: start the compose of src/test/integration, or set MONERO_C_TEST_DAEMON_URI")
    }

    @AfterTest
    fun closeClient() {
        http.close()
    }

    @Test
    fun testCreateConnectAndSync() {
        // a wallet from a seed connects to the daemon after it is created, which a full wallet can be at any time
        val fromSeedPath = walletPath()
        val seedConfig = config(fromSeedPath)
        seedConfig.seed = seed
        val fromSeed = MoneroWalletFull.createWalletFromSeed(seedConfig)
        try {
            assertTrue(MoneroWalletFull.walletExists(fromSeedPath))
            assertEquals(seed, fromSeed.getSeed())
            assertEquals(primaryAddress, fromSeed.getPrimaryAddress())
            assertEquals(MoneroNetworkType.REGTEST, fromSeed.getNetworkType())
            assertEquals(fromSeedPath, fromSeed.getPath())
            assertEquals(0L, fromSeed.getRestoreHeight())
            assertEquals(null, fromSeed.getDaemonConnection())
            fromSeed.setDaemonConnection(connection)
            assertEquals(daemonUri, fromSeed.getDaemonConnection()?.uri)
            assertEquals(true, fromSeed.isConnectedToDaemon())

            // the wallet has scanned nothing before its first sync, and the blocks that the other test of this
            // class mines pay no wallet of this one, so its balance is still zero
            assertEquals(BigInteger.ZERO, fromSeed.getBalance())

            // a block on the chain gives the sync something to scan, whichever test of this class runs first
            mineBlocks(2, unwatchedAddress())

            // the wallet syncs the chain of the daemon from its first block
            fromSeed.sync()
            assertEquals(fromSeed.getDaemonHeight(), fromSeed.getHeight())
            assertTrue(fromSeed.getHeight() > 2, "the chain has no blocks to sync")
            assertEquals(true, fromSeed.isDaemonSynced())
            assertEquals(true, fromSeed.isSynced())

            // the wallet syncs in the background too, and it stops when it is asked to
            fromSeed.startSyncing(20)
            Thread.sleep(500)
            fromSeed.stopSyncing()
            assertEquals(true, fromSeed.isSynced())

            // a wallet from keys is the same account as the wallet from the seed
            val fromKeysPath = walletPath()
            val keysConfig = config(fromKeysPath)
            keysConfig.primaryAddress = primaryAddress
            keysConfig.privateViewKey = privateViewKey
            keysConfig.privateSpendKey = privateSpendKey
            val fromKeys = MoneroWalletFull.createWalletFromKeys(keysConfig)
            try {
                assertEquals(primaryAddress, fromKeys.getPrimaryAddress())
                assertEquals(privateViewKey, fromKeys.getPrivateViewKey())
                assertEquals(privateSpendKey, fromKeys.getPrivateSpendKey())
                assertEquals(false, fromKeys.isViewOnly())
            } finally {
                fromKeys.close()
            }

            // a random wallet has a seed of its own, and a create config connects it to the daemon as it is created
            val randomPath = walletPath()
            val randomConfig = config(randomPath)
            randomConfig.restoreHeight = null
            randomConfig.server = connection
            val random = MoneroWalletFull.createWallet(randomConfig)
            try {
                assertEquals(25, random.getSeed().split(" ").size)
                assertEquals(MoneroNetworkType.REGTEST, random.getNetworkType())
                assertEquals(daemonUri, random.getDaemonConnection()?.uri)
                assertEquals(true, random.isConnectedToDaemon())
                random.sync()
                assertEquals(random.getDaemonHeight(), random.getHeight())
            } finally {
                random.close()
            }
        } finally {
            fromSeed.close()
        }
    }

    @Test
    fun testSendSaveAndReopen() {
        // the sender spends the seed of the test wallet, and the receiver receives with the seed of the mining
        // wallet; both are created with their files and connected to the daemon
        val senderPath = walletPath()
        val senderConfig = config(senderPath)
        senderConfig.seed = seed
        val sender = MoneroWalletFull.createWalletFromSeed(senderConfig)
        sender.setDaemonConnection(connection)
        val receiverPath = walletPath()
        val receiverConfig = config(receiverPath)
        receiverConfig.seed = receiverSeed
        val receiver = MoneroWalletFull.createWalletFromSeed(receiverConfig)
        receiver.setDaemonConnection(connection)
        var before = BigInteger.ZERO
        var fee = BigInteger.ZERO
        try {
            val senderAddress = sender.getPrimaryAddress()
            val receiverAddress = receiver.getPrimaryAddress()

            // mine to the sender, then enough blocks for its coinbases to spend, and for the decoys of its rings:
            // a coinbase of a regtest chain locks for 60 blocks, every output of the chain is a coinbase, and the
            // wallet needs sixteen unlocked ones for every ring it builds
            mineBlocks(5, senderAddress)
            mineBlocks(130, unwatchedAddress())
            sender.sync()
            before = sender.getBalance()
            assertTrue(before > BigInteger.ZERO, "the sender has no funds")
            assertEquals(before, sender.getUnlockedBalance(), "the coinbases of the sender are still locked")

            // a transfer to an address that is not one is refused before the daemon is asked anything
            val badAddress = assertFailsWith<MoneroError> { sender.createTxs(txConfig("notanaddress", amount)) }
            assertEquals("Invalid destination address", badAddress.message)

            // the transfer is created without relaying, and relays from its metadata
            val txs = sender.createTxs(txConfig(receiverAddress, amount))
            assertEquals(1, txs.size)
            val tx = txs[0]
            fee = assertNotNull(tx.fee)
            assertTrue(fee > BigInteger.ZERO)
            val hash = assertNotNull(tx.hash)
            val metadata = assertNotNull(tx.metadata)
            assertEquals(hash, sender.relayTx(metadata))

            // a block holds the transaction, and the receiver reads exactly what was sent
            mineBlocks(1, unwatchedAddress())
            receiver.sync()
            assertEquals(amount, receiver.getBalance())
            val received = receiver.getTxs().single { candidate -> candidate.hash == hash }
            assertEquals(true, received.isIncoming)
            assertEquals(amount, received.incomingTransfers!!.single().amount)

            // the sender's balance is the one before, less the amount and the fee
            sender.sync()
            assertEquals(before.subtract(amount).subtract(fee), sender.getBalance())
            val sent = sender.getTxs().single { candidate -> candidate.hash == hash }
            assertEquals(fee, sent.fee)
            assertEquals(false, sent.isIncoming)

            // the sender's file is written with its chain, and closing it lets another wallet open it: the balance
            // and the seed survive the close. the height before the sync of the reopened wallet is the one the file
            // carries, which is the save: a wallet that re-scans starts from the restore height
            val savedHeight = sender.getHeight()
            sender.save()
            sender.close()
            val saved = MoneroWalletFull.openWallet(config(senderPath))
            try {
                assertEquals(seed, saved.getSeed())
                assertEquals(primaryAddress, saved.getPrimaryAddress())
                assertEquals(senderPath, saved.getPath())
                assertEquals(null, saved.getDaemonConnection())
                assertEquals(savedHeight, saved.getHeight())
                saved.setDaemonConnection(connection)
                saved.sync()
                assertEquals(before.subtract(amount).subtract(fee), saved.getBalance())
            } finally {
                saved.close()
            }

            // the receiver is closed with a save, and a wallet that opens it reads the same
            receiver.close(true)
            val reopened = MoneroWalletFull.openWallet(config(receiverPath))
            try {
                assertEquals(receiverSeed, reopened.getSeed())
                reopened.setDaemonConnection(connection)
                reopened.sync()
                assertEquals(amount, reopened.getBalance())
            } finally {
                reopened.close()
            }
        } finally {
            sender.close()
            receiver.close()
        }
    }

    // ------------------------------- PRIVATE ------------------------------------

    // the daemon takes a while to come up after the compose starts it, so the probe retries for a minute before it
    // skips
    private val isDaemonUp: Boolean by lazy {
        (1..60).any {
            try {
                post("""{"jsonrpc": "2.0", "id": "0", "method": "get_info"}""").contains("\"height\"")
                true
            } catch (e: Exception) {
                Thread.sleep(1000)
                false
            }
        }
    }

    @Test
    fun testMultisig() {
        testMultisig(2, 2)
        testMultisig(2, 3)
    }

    /**
     * Run the multisig story of the java suite for m of n: the participants prepare and make their wallets multisig,
     * exchange keys until the wallet is final, the coinbases of five blocks fund it, a transfer needs the signatures
     * of the threshold, and the participants import each others hexes to see it (TestMoneroWalletCommon.java:3815).
     *
     * @param m is the number of signatures that a transfer needs
     * @param n is the number of participants
     */
    private fun testMultisig(m: Int, n: Int) {
        val participants = (0 until n).map {
            val config = MoneroWalletConfig()
            config.networkType = MoneroNetworkType.REGTEST
            MoneroWalletFull.createWallet(config).also { wallet -> wallet.setDaemonConnection(connection) }
        }
        try {
            // each participant prepares the hex that the others make their wallet multisig with
            val prepared = participants.map { it.prepareMultisig() }

            // a hex that is not a prepared multisig hex is refused
            val badMake = assertFailsWith<MoneroError> { participants[0].makeMultisig(listOf("asd", "dsa"), m, "") }
            assertEquals("Kex message unexpectedly small.", badMake.message)

            // each participant makes its wallet multisig from the prepared hexes of its peers
            var hexes = participants.mapIndexed { i, wallet ->
                wallet.makeMultisig(prepared.filterIndexed { j, _ -> j != i }, m, "")
            }

            // the wallet is multisig but not final, so it has no seed to read
            val notFinal = assertFailsWith<MoneroError> { participants[0].getSeed() }
            assertEquals("This wallet is multisig, but not yet finalized", notFinal.message)

            // the participants exchange keys n - m + 1 times, and the last round carries the address
            var address: String? = null
            repeat(n - m + 1) { round ->
                val nextHexes = mutableListOf<String>()
                for (i in participants.indices) {
                    val badExchange = assertFailsWith<MoneroError> { participants[i].exchangeMultisigKeys(listOf("asd", "dsa"), "") }
                    assertTrue(badExchange.message!!.isNotEmpty())

                    val result = participants[i].exchangeMultisigKeys(hexes.filterIndexed { j, _ -> j != i }, "")
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

            // the final wallet is multisig, ready, and answers its info with the threshold and the participants
            for (wallet in participants) {
                assertEquals(address, wallet.getPrimaryAddress())
                val info = wallet.getMultisigInfo()
                assertEquals(true, info.isMultisig)
                assertEquals(true, info.isReady)
                assertEquals(m, info.threshold)
                assertEquals(n, info.numParticipants)
                assertEquals(true, wallet.isMultisig())
                assertTrue(wallet.getSeed()!!.isNotEmpty())
                assertEquals(false, wallet.isViewOnly())
            }

            // five coinbases fund the multisig wallet, and the chain grows past the locks and the ring window
            mineBlocks(5, address!!)
            mineBlocks(130, unwatchedAddress())
            for (wallet in participants) wallet.sync()
            val before = participants[0].getBalance()
            assertTrue(before > BigInteger.ZERO, "the multisig wallet has no funds")
            assertEquals(true, participants[0].isMultisigImportNeeded(), "a funded multisig wallet needs the hexes of its peers")
            val amount = BigInteger.valueOf(5000000000000L)

            // a participant that did not import the hexes of its peers can not build the transfer of the outputs
            // that they share, which the java suite asserts the same way (TestMoneroWalletCommon.java:4125)
            val unsynchronized = assertFailsWith<MoneroError> { participants[0].createTxs(txConfig(address!!, amount)) }
            assertEquals("No transaction created", unsynchronized.message)

            // the participants import each others hexes, which is what a transfer of the shared outputs needs
            synchronizeParticipants(participants)

            // a transfer of the threshold wallet is made from its hex and signed by enough participants
            val txs = participants[0].createTxs(txConfig(address!!, amount))
            assertEquals(1, txs.size)
            var multisigTxHex = assertNotNull(txs[0].txSet?.multisigTxHex, "the transfer of a multisig wallet made no multisig hex")
            for (i in 1 until m) {
                val signed = participants[i].signMultisigTxHex(multisigTxHex)
                multisigTxHex = assertNotNull(signed.signedMultisigTxHex)
            }

            // one participant submits the signed transfer, and the participants import each others hexes to see it
            val hashes = participants[0].submitMultisigTxHex(multisigTxHex)
            assertTrue(hashes.isNotEmpty())
            synchronizeParticipants(participants)
            val query = MoneroTxQuery()
            query.hashes = hashes
            assertEquals(hashes.size, participants[0].getTxs(query).size)
        } finally {
            for (wallet in participants) {
                try {
                    wallet.close()
                } catch (e: MoneroError) {
                    // a participant that failed to initialize is closed already
                }
            }
        }
    }

    /**
     * Sync the participants of a multisig wallet and import each others multisig hex, which shows them the outputs
     * that the others know (TestMoneroWalletCommon.java:4296).
     *
     * @param participants are the wallets of the multisig participants
     */
    private fun synchronizeParticipants(participants: List<MoneroWalletFull>) {
        val hexes = participants.map { wallet ->
            wallet.sync()
            wallet.exportMultisigHex()
        }
        for (i in participants.indices) {
            participants[i].sync()
            participants[i].importMultisigHex(hexes.filterIndexed { j, _ -> j != i }, true)
        }
    }

    private fun config(path: String): MoneroWalletConfig {
        val config = MoneroWalletConfig()
        config.path = path
        config.password = password
        config.networkType = MoneroNetworkType.REGTEST
        config.restoreHeight = 0
        return config
    }

    private fun walletPath(): String {
        return tempDir.resolve("wallet-${System.nanoTime()}").toString()
    }

    private fun txConfig(address: String, amount: BigInteger): MoneroTxConfig {
        val config = MoneroTxConfig()
        config.accountIndex = 0
        config.destinations = listOf(MoneroDestination(address, amount))
        config.relay = false
        return config
    }

    /**
     * An address of mainnet that no wallet of the tests watches, which the blocks that the send needs pay their
     * coinbases to: nobody's balance moves with them. A keys-only wallet has the address without the daemon.
     *
     * @return an address that no wallet watches
     */
    private fun unwatchedAddress(): String {
        val config = MoneroWalletConfig()
        config.networkType = MoneroNetworkType.MAINNET
        return MoneroWalletKeys.createWalletRandom(config).use { miner -> miner.getPrimaryAddress() }
    }

    /**
     * Mine blocks to an address with the daemon. The daemon mines a large number of blocks slower than a call can
     * wait for it, so the blocks are mined in batches.
     *
     * @param numBlocks is the number of blocks to mine
     * @param address is the address that the coinbases pay
     * @throws MoneroError if the daemon does not mine them
     */
    private fun mineBlocks(numBlocks: Int, address: String) {
        var remaining = numBlocks
        while (remaining > 0) {
            val batch = min(25, remaining)
            val response = post("""{"jsonrpc": "2.0", "id": "0", "method": "generateblocks",""" +
                """ "params": {"amount_of_blocks": $batch, "wallet_address": "$address", "starting_nonce": 0}}""", Duration.ofSeconds(120))
            if (!response.contains("\"status\": \"OK\"")) throw MoneroError("generateblocks did not mine: $response")
            remaining -= batch
        }
    }

    /**
     * Post a body to the json_rpc of the daemon, and return what it answers.
     *
     * @param body is the JSON of the request
     * @param timeout is how long the call may take
     * @return the JSON of the response
     * @throws MoneroError if the daemon does not answer 200
     */
    private fun post(body: String, timeout: Duration = Duration.ofSeconds(30)): String {
        val request = HttpRequest.newBuilder(URI.create(daemonUri + "/json_rpc"))
            .header("Content-Type", "application/json")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw MoneroError("POST /json_rpc answered ${response.statusCode()}: ${response.body()}")
        return response.body()
    }
}
