package monero.wallet

import java.math.BigInteger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.NotImplemented
import monero.RegtestSupport
import monero.common.MoneroError
import monero.daemon.MoneroDaemonRpc
import monero.wallet.model.MoneroSyncResult
import monero.wallet.model.MoneroTxQuery
import org.junit.jupiter.api.Tag

/**
 * The sync of a full wallet, against a regtest node, like the tests of monero-python (tests/test_monero_wallet_full.py) that sync a wallet
 * with a random seed, from a seed with a restore height and a start height, and that check that the wallets do not hear each other. The wallet
 * of the seed is the one of [TxHistoryWallet], whose first payment is at the height that monero-python knows as FIRST_RECEIVE_HEIGHT. They
 * need monero-c and the regtest node of src/test/docker.
 */
@Tag("integration")
class MoneroWalletFullSyncIntegrationTest {

    // the seed that the tests restore wallets from, and the height of the first block that pays it
    private val seed: String by lazy { TxHistoryWallet.get(FullTestWallets).getSeed() }
    private val firstReceiveHeight: Long by lazy {
        TxHistoryWallet.get(FullTestWallets).getTxs(MoneroTxQuery().also { it.isConfirmed = true }).minOf { it.height!! }
    }

    @BeforeTest
    fun requireLibraryAndNode() {
        FullTestWallets.assumeAvailable()
    }

    // Can sync a wallet with a randomly generated seed: it syncs from its restore height, which monero-c sets at 0 for a wallet that it creates
    // on the regtest chain, since the estimate of the date that a new wallet starts from is past that chain, where monero-python expects the
    // height of the chain, and it finds what a wallet of its seed that is restored from there finds
    @Test
    fun testSyncRandom() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            FullTestWallets.create().use { wallet ->
                val restoreHeight = wallet.getRestoreHeight()
                val daemonHeight = daemon.getHeight()
                val startHeight = maxOf(restoreHeight, 1L)

                // the wallet before it syncs
                assertEquals(RegtestSupport.daemonUri, wallet.getDaemonConnection()!!.uri)
                assertEquals(daemonHeight, wallet.getDaemonHeight())
                assertTrue(restoreHeight <= daemonHeight)
                assertTrue(wallet.isConnectedToDaemon())
                assertFalse(wallet.isSynced())
                assertEquals(1L, wallet.getHeight())

                // sync it
                val progress = SyncProgressTester(wallet, startHeight, daemonHeight)
                val result = wallet.sync(progress)
                progress.onDone(wallet.getDaemonHeight())
                assertEquals(emptyList(), progress.errors)

                assertTrue(wallet.isConnectedToDaemon())
                assertTrue(wallet.isSynced())
                assertEquals(daemonHeight - startHeight, result.numBlocksFetched)
                assertEquals(false, result.receivedMoney)
                assertEquals(daemon.getHeight(), wallet.getHeight())

                // sync it with the defaults
                wallet.sync()
                assertTrue(wallet.isSynced())
                assertEquals(daemon.getHeight(), wallet.getHeight())

                // compare it with a ground truth
                FullTestWallets.create(wallet.getSeed(), restoreHeight).use { groundTruth ->
                    groundTruth.sync()
                    WalletEquality.assertFullOnChain(groundTruth, wallet)
                }
            }
        }
    }

    // Can not sync a wallet that is not connected to a node
    @Test
    fun testSyncUnconnectedWallet() {
        FullTestWallets.createUnconnected().use { wallet ->
            val error = assertFailsWith<MoneroError> { wallet.sync() }
            assertEquals("Wallet is not connected to daemon", error.message)
        }
    }

    // Does not interfere with other wallet notifications: two wallets of the same seed hear their own syncs, and a listener hears the wallet it
    // is registered with
    @Test
    fun testWalletsDoNotInterfere() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            val seed = FullTestWallets.create().use { it.getSeed() }
            val height = daemon.getHeight()
            val restoreHeight = height - 5
            FullTestWallets.create(seed, restoreHeight).use { wallet1 ->
                FullTestWallets.create(seed, restoreHeight).use { wallet2 ->
                    val tester1 = SyncProgressTester(wallet1, restoreHeight, height)
                    val tester2 = SyncProgressTester(wallet2, restoreHeight, height)
                    wallet1.addListener(tester1)
                    wallet2.addListener(tester2)

                    // sync the first wallet, and the second is not notified
                    wallet1.sync()
                    assertNotNull(tester1.prevHeight)
                    assertNull(tester2.prevHeight)

                    // sync the second, and a listener that is added to the first is not notified
                    val tester3 = SyncProgressTester(wallet1, restoreHeight, height)
                    wallet1.addListener(tester3)
                    wallet2.sync()
                    assertNotNull(tester2.prevHeight)
                    assertNull(tester3.prevHeight)
                    assertEquals(emptyList(), tester1.errors + tester2.errors + tester3.errors)
                }
            }
        }
    }

    // Can sync a wallet created from seed from the genesis
    @Test
    fun testSyncSeedFromGenesis() {
        testSyncSeed(null, null, skipGroundTruth = true)
    }

    // Can sync a wallet created from seed from a restore height
    @Test
    fun testSyncSeedFromRestoreHeight() {
        testSyncSeed(null, firstReceiveHeight)
    }

    // Can sync a wallet created from seed from a start height
    @Test
    fun testSyncSeedFromStartHeight() {
        testSyncSeed(firstReceiveHeight, null, testPostSync = true)
    }

    // Can sync a wallet created from seed from a start height less than the restore height
    @Test
    fun testSyncSeedStartHeightLessThanRestoreHeight() {
        testSyncSeed(firstReceiveHeight, firstReceiveHeight + 3)
    }

    // Can sync a wallet created from seed from a start height greater than the restore height. From 1000 on, wallet2 starts a sync at the
    // checkpoint of mainnet below the start height, which a regtest chain does not have, and goes on from block 1001: the wallet has the
    // payments between there and the start height, where monero-python expects its first transaction after the first payment (the TODO.md of
    // monero-c has it). It passes on a chain that is shorter
    @Test
    @NotImplemented
    fun testSyncSeedStartHeightGreaterThanRestoreHeight() {
        testSyncSeed(firstReceiveHeight + 3, firstReceiveHeight)
    }

    // Can sync a wallet created from keys: it is the wallet of the seed, and it finds what a wallet of the seed that restores from the same
    // height finds
    @Test
    fun testSyncWalletFromKeys() {
        // the wallet of the history leaves a transaction in the pool when it is made. The transactions of the pool are not on the chain: a block
        // takes them, as monero-python waits for them to clear the pool
        val shared = TxHistoryWallet.get(FullTestWallets)
        RegtestSupport.mineBlocks(1)

        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            val keys = FullTestWallets.createFromKeys(
                shared.getPrimaryAddress(), shared.getPrivateViewKey(), shared.getPrivateSpendKey(), firstReceiveHeight
            )
            keys.use {
                FullTestWallets.create(seed, firstReceiveHeight).use { groundTruth ->
                    // it is the wallet of the seed
                    assertEquals(groundTruth.getSeed(), keys.getSeed())
                    assertEquals(groundTruth.getPrimaryAddress(), keys.getPrimaryAddress())
                    assertEquals(groundTruth.getPrivateViewKey(), keys.getPrivateViewKey())
                    assertEquals(groundTruth.getPublicViewKey(), keys.getPublicViewKey())
                    assertEquals(groundTruth.getPrivateSpendKey(), keys.getPrivateSpendKey())
                    assertEquals(groundTruth.getPublicSpendKey(), keys.getPublicSpendKey())
                    assertEquals(firstReceiveHeight, groundTruth.getRestoreHeight())
                    assertTrue(keys.isConnectedToDaemon())
                    assertFalse(keys.isSynced())

                    // sync it
                    val progress = SyncProgressTester(keys, firstReceiveHeight, keys.getDaemonMaxPeerHeight())
                    val result = keys.sync(progress)
                    progress.onDone(keys.getDaemonHeight())
                    assertEquals(emptyList(), progress.errors)

                    assertTrue(keys.isSynced())
                    assertBlocksFetched(result, keys.getDaemonHeight(), firstReceiveHeight)
                    assertEquals(true, result.receivedMoney)
                    assertEquals(daemon.getHeight(), keys.getHeight())
                    assertEquals(daemon.getHeight(), keys.getDaemonHeight())

                    // it syncs from its restore height, so its first transaction is the first payment. monero-python reads the transactions of
                    // the wallet that it restored from, and not of this one
                    val txHeight = keys.getTxs(MoneroTxQuery().also { it.isConfirmed = true }).minOf { assertNotNull(it.height) }
                    assertEquals(firstReceiveHeight, txHeight)

                    // compare it with the wallet of the seed
                    groundTruth.sync()
                    WalletEquality.assertFullOnChain(groundTruth, keys)
                }
            }
        }
    }

    // Can start and stop syncing: it succeeds while the wallet is not connected, it can be stopped as many times as it is asked, and it syncs the
    // wallet by itself
    @Test
    fun testStartStopSyncing() {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            // a wallet that is not connected starts syncing, and syncs when a daemon becomes reachable
            FullTestWallets.createUnconnected().use { wallet ->
                assertTrue(wallet.getSeed().isNotEmpty())
                assertEquals(1L, wallet.getHeight())
                assertEquals(BigInteger.ZERO, wallet.getBalance())
                wallet.startSyncing()
            }

            // a wallet that is connected after it was made
            FullTestWallets.createUnconnected().use { wallet ->
                assertTrue(wallet.getSeed().isNotEmpty())
                wallet.setDaemonConnection(RegtestSupport.daemonConnection())
                assertEquals(1L, wallet.getHeight())
                assertFalse(wallet.isSynced())
                assertEquals(BigInteger.ZERO, wallet.getBalance())
                val chainHeight = wallet.getDaemonHeight()
                wallet.setRestoreHeight(chainHeight - 3)
                wallet.startSyncing()
                assertEquals(chainHeight - 3, wallet.getRestoreHeight())
                val connection = assertNotNull(wallet.getDaemonConnection())
                assertEquals(RegtestSupport.daemonUri, connection.uri)
                assertEquals(RegtestSupport.daemonUsername, connection.username)
                assertEquals(RegtestSupport.daemonPassword, connection.password)
                wallet.stopSyncing()
                wallet.sync()
                wallet.stopSyncing()
                wallet.stopSyncing()
            }

            // a wallet that syncs by itself once it started
            val restoreHeight = maxOf(daemon.getHeight() - 100, 1L)
            FullTestWallets.create(seed, restoreHeight).use { wallet ->
                assertEquals(1L, wallet.getHeight())
                assertEquals(restoreHeight, wallet.getRestoreHeight())
                assertFalse(wallet.isSynced())
                assertEquals(BigInteger.ZERO, wallet.getBalance())
                wallet.startSyncing(500)
                try {
                    val deadline = System.nanoTime() + 60_000_000_000L
                    while (wallet.getHeight() <= 1) {
                        if (System.nanoTime() > deadline) throw AssertionError("The wallet did not start syncing")
                        Thread.sleep(250)
                    }
                } finally {
                    wallet.stopSyncing()
                }
            }
        }
    }

    // ------------------------------- PRIVATE ------------------------------------

    // monero-python expects the blocks from the start to the chain. wallet2 starts a sync that is from 1000 on at a checkpoint of mainnet, which
    // a regtest chain does not have, and it counts the blocks from block 1001 (the TODO.md of monero-c has it), so there it counts more
    private fun assertBlocksFetched(result: MoneroSyncResult, daemonHeight: Long, startHeight: Long) {
        val fetched = assertNotNull(result.numBlocksFetched)
        val expected = daemonHeight - startHeight
        if (startHeight < 1000) {
            assertEquals(expected, fetched)
        } else {
            assertTrue(fetched >= expected, "$fetched blocks fetched, and the chain is $expected blocks past the start")
        }
    }

    /**
     * Restore a wallet from the seed, sync it, and test what it notified, what it found and, unless it is skipped, that it is equal to a ground
     * truth wallet that syncs the same way. The notifications are tested by [WalletSyncTester], which is registered with the wallet, and by a
     * tester of the progress that the sync is called with.
     *
     * @param startHeight is the height to sync from, or null for the restore height
     * @param restoreHeight is the restore height of the wallet, or null for the genesis block
     * @param skipGroundTruth is whether not to compare the wallet with a ground truth wallet
     * @param testPostSync is whether to test that the wallet is notified of a block that comes after the sync, when it syncs by itself
     */
    private fun testSyncSeed(startHeight: Long?, restoreHeight: Long?, skipGroundTruth: Boolean = false, testPostSync: Boolean = false) {
        MoneroDaemonRpc(RegtestSupport.daemonConnection()).use { daemon ->
            if (startHeight != null && restoreHeight != null) assertTrue(startHeight <= firstReceiveHeight || restoreHeight <= firstReceiveHeight)

            // the transactions of the pool are not on the chain: a block takes them, as monero-python waits for them to clear the pool
            RegtestSupport.mineBlocks(1)

            FullTestWallets.create(seed, restoreHeight).use { wallet ->
                // the heights that the sync is expected to have: monero-python asks the daemon for the height of its peers, which this wallet cannot
                val restore = restoreHeight ?: 0L
                val startExpected = maxOf(startHeight ?: restore, 1L)
                val endExpected = wallet.getDaemonHeight()

                // the wallet before it syncs
                assertTrue(wallet.isConnectedToDaemon())
                assertFalse(wallet.isSynced())
                assertEquals(1L, wallet.getHeight())
                assertEquals(restore, wallet.getRestoreHeight())

                // sync it, with a listener that is registered and one that the sync is called with
                val walletTester = WalletSyncTester(wallet, startExpected, endExpected)
                wallet.addListener(walletTester)
                val progressTester = SyncProgressTester(wallet, startExpected, endExpected)
                val result = if (startHeight != null) wallet.sync(startHeight, progressTester) else wallet.sync(progressTester)
                progressTester.onDone(wallet.getDaemonHeight())
                walletTester.onDone(wallet.getDaemonHeight())

                // the wallet after it syncs
                assertTrue(wallet.isSynced())
                assertBlocksFetched(result, wallet.getDaemonHeight(), startExpected)
                assertEquals(true, result.receivedMoney)
                assertEquals(daemon.getHeight(), wallet.getDaemonHeight())

                // its first transaction is the first payment, or one after it when the sync started later. The transactions are not in the order of
                // their heights, where monero-python takes the first of them
                val txHeight = wallet.getTxs(MoneroTxQuery().also { it.isConfirmed = true }).minOf { assertNotNull(it.height) }
                if (startExpected > firstReceiveHeight) {
                    assertTrue(
                        txHeight > firstReceiveHeight,
                        "the first transaction is at $txHeight, the first payment is at $firstReceiveHeight, and the sync started at $startExpected"
                    )
                } else {
                    assertEquals(firstReceiveHeight, txHeight)
                }

                // sync it again with the defaults: a block can be added to the chain meanwhile
                val again = wallet.sync()
                assertTrue(wallet.isSynced())
                assertEquals(daemon.getHeight(), wallet.getHeight())
                assertTrue(again.numBlocksFetched == 0L || again.numBlocksFetched == 1L)
                assertEquals(false, again.receivedMoney)

                // compare it with a ground truth wallet that syncs the same way
                if (!skipGroundTruth) {
                    FullTestWallets.create(seed, restoreHeight).use { groundTruth ->
                        groundTruth.sync(startHeight ?: restore)
                        WalletEquality.assertFullOnChain(groundTruth, wallet)
                    }
                }

                if (testPostSync) testPostSync(wallet, walletTester)
                assertEquals(emptyList(), progressTester.errors + walletTester.errors)
            }
        }
    }

    // The wallet that is registered with the sync of the wallet is notified of the blocks that come after it, and the listener that the sync was
    // called with is not
    private fun testPostSync(wallet: MoneroWalletNative, walletTester: WalletSyncTester) {
        wallet.startSyncing(1000)
        try {
            RegtestSupport.mineBlocks(1)
            val deadline = System.nanoTime() + 60_000_000_000L
            while (!(walletTester.notifiedAfterDone && walletTester.newBlockAfterDone)) {
                if (System.nanoTime() > deadline) throw AssertionError("The wallet was not notified of the block that came after the sync")
                Thread.sleep(250)
            }
        } finally {
            wallet.stopSyncing()
        }
    }
}
