package monero.wallet

import com.fasterxml.jackson.databind.JavaType
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import monero.ModelTestSupport
import monero.ModelTestSupport.assertSameJson
import monero.ModelTestSupport.listOf
import monero.ModelTestSupport.read
import monero.ModelTestSupport.tree
import monero.ModelTestSupport.write
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.common.MoneroRpcConnection
import monero.daemon.model.MoneroBlock
import monero.daemon.model.MoneroNetworkType
import monero.daemon.model.MoneroOutput
import monero.daemon.model.MoneroTx
import monero.daemon.model.MoneroVersion
import monero.wallet.model.MoneroAccount
import monero.wallet.model.MoneroAccountTag
import monero.wallet.model.MoneroAddressBookEntry
import monero.wallet.model.MoneroAddressType
import monero.wallet.model.MoneroBlockWallet
import monero.wallet.model.MoneroCheckReserve
import monero.wallet.model.MoneroCheckTx
import monero.wallet.model.MoneroDecodedAddress
import monero.wallet.model.MoneroDestination
import monero.wallet.model.MoneroIncomingTransfer
import monero.wallet.model.MoneroIntegratedAddress
import monero.wallet.model.MoneroKeyImageExportResult
import monero.wallet.model.MoneroKeyImageImportResult
import monero.wallet.model.MoneroMessageSignatureResult
import monero.wallet.model.MoneroMessageSignatureType
import monero.wallet.model.MoneroMultisigInfo
import monero.wallet.model.MoneroMultisigInitResult
import monero.wallet.model.MoneroMultisigSignResult
import monero.wallet.model.MoneroOutgoingTransfer
import monero.wallet.model.MoneroOutputQuery
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroSubaddress
import monero.wallet.model.MoneroSyncResult
import monero.wallet.model.MoneroTransfer
import monero.wallet.model.MoneroTransferQuery
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxPriority
import monero.wallet.model.MoneroTxQuery
import monero.wallet.model.MoneroTxSet
import monero.wallet.model.MoneroTxSetsResult
import monero.wallet.model.MoneroTxWallet
import monero.wallet.model.MoneroWalletBlocksResult
import monero.wallet.model.MoneroWalletConfig
import monero.wallet.model.MoneroWalletListener
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Serialization and deserialization of the wallet data model.
 *
 * The JSON in the fixtures of the wallet was written by monero-c (and so by monero-cpp) from a regtest node, with a
 * full wallet and a wallet RPC. The models have to read it, and to write it back unchanged. What the fixtures do not
 * have is written by hand in the tests, in the same shape.
 *
 * Every test runs without monero-c, a node or the network.
 */
class MoneroWalletModelTest {

    private val address = "42jhNF2LEE4mqDqBQhVo8dKjTxxfYo6bmjUZfXmBzKUdQ4Kp3tGDfxXkuxJLiHgeKxqRRwFNzUZc1kRyPWDnjsNbSz5Wqdo"
    private val two64 = BigInteger.ONE.shiftLeft(64)

    private fun type(type: Class<*>): JavaType = MoneroJson.mapper.constructType(type)

    private val fixtureTypes: Map<String, JavaType> = mapOf(
        "account_b_with_subaddresses" to type(MoneroAccount::class.java),
        "account_created" to type(MoneroAccount::class.java),
        "account_with_subaddresses" to type(MoneroAccount::class.java),
        "account_with_subaddresses_after" to type(MoneroAccount::class.java),
        "account_without_subaddresses" to type(MoneroAccount::class.java),
        "accounts_after" to listOf(MoneroAccount::class.java),
        "accounts_with_subaddresses" to listOf(MoneroAccount::class.java),
        "accounts_without_subaddresses" to listOf(MoneroAccount::class.java),
        "address_book_entries" to listOf(MoneroAddressBookEntry::class.java),
        "check_reserve" to type(MoneroCheckReserve::class.java),
        "check_reserve_bad" to type(MoneroCheckReserve::class.java),
        "check_tx" to type(MoneroCheckTx::class.java),
        "check_tx_proof_bad" to type(MoneroCheckTx::class.java),
        "check_tx_proof_good" to type(MoneroCheckTx::class.java),
        "integrated_address" to type(MoneroIntegratedAddress::class.java),
        "integrated_address_decoded" to type(MoneroIntegratedAddress::class.java),
        "key_images_export" to type(MoneroKeyImageExportResult::class.java),
        "key_images_import_result" to type(MoneroKeyImageImportResult::class.java),
        "listener_output_received_first" to type(MoneroWalletBlocksResult::class.java),
        "listener_output_received_last" to type(MoneroWalletBlocksResult::class.java),
        "listener_output_spent_first" to type(MoneroWalletBlocksResult::class.java),
        "listener_output_spent_last" to type(MoneroWalletBlocksResult::class.java),
        "message_signature_spend" to type(MoneroMessageSignatureResult::class.java),
        "message_signature_view" to type(MoneroMessageSignatureResult::class.java),
        "message_signature_wrong_message" to type(MoneroMessageSignatureResult::class.java),
        "multisig_exchange_result_1" to type(MoneroMultisigInitResult::class.java),
        "multisig_exchange_result_2" to type(MoneroMultisigInitResult::class.java),
        "multisig_info_after_make" to type(MoneroMultisigInfo::class.java),
        "multisig_info_none" to type(MoneroMultisigInfo::class.java),
        "multisig_info_ready" to type(MoneroMultisigInfo::class.java),
        "outputs_frozen" to type(MoneroWalletBlocksResult::class.java),
        "outputs_miner" to type(MoneroWalletBlocksResult::class.java),
        "outputs_of_b" to type(MoneroWalletBlocksResult::class.java),
        "outputs_of_tx" to type(MoneroWalletBlocksResult::class.java),
        "outputs_spent" to type(MoneroWalletBlocksResult::class.java),
        "rpc_connection" to type(MoneroRpcConnection::class.java),
        "seed_languages" to listOf(String::class.java),
        "subaddress_created" to type(MoneroSubaddress::class.java),
        "subaddress_of_address" to type(MoneroSubaddress::class.java),
        "subaddresses" to listOf(MoneroSubaddress::class.java),
        "submitted_hashes" to listOf(String::class.java),
        "sync_result" to type(MoneroSyncResult::class.java),
        "sync_result_b" to type(MoneroSyncResult::class.java),
        "sync_result_c" to type(MoneroSyncResult::class.java),
        "transfers_incoming_only" to type(MoneroWalletBlocksResult::class.java),
        "transfers_miner" to type(MoneroWalletBlocksResult::class.java),
        "transfers_outgoing" to type(MoneroWalletBlocksResult::class.java),
        "transfers_pool" to type(MoneroWalletBlocksResult::class.java),
        "transfers_to_subaddress" to type(MoneroWalletBlocksResult::class.java),
        "tx_config_from_uri" to type(MoneroTxConfig::class.java),
        "tx_notes" to listOf(String::class.java),
        "tx_outgoing_confirmed" to type(MoneroWalletBlocksResult::class.java),
        "tx_sets_created" to type(MoneroTxSetsResult::class.java),
        "tx_sets_created_many" to type(MoneroTxSetsResult::class.java),
        "tx_sets_created_relayed" to type(MoneroTxSetsResult::class.java),
        "tx_sets_described" to type(MoneroTxSetsResult::class.java),
        "tx_sets_signed" to type(MoneroTxSetsResult::class.java),
        "tx_sets_swept_dust" to type(MoneroTxSetsResult::class.java),
        "tx_sets_swept_output" to type(MoneroTxSetsResult::class.java),
        "tx_sets_swept_unlocked" to type(MoneroTxSetsResult::class.java),
        "tx_sets_unsigned" to type(MoneroTxSetsResult::class.java),
        "tx_to_subaddress" to type(MoneroWalletBlocksResult::class.java),
        "txs_empty" to type(MoneroWalletBlocksResult::class.java),
        "txs_incoming_only" to type(MoneroWalletBlocksResult::class.java),
        "txs_miner" to type(MoneroWalletBlocksResult::class.java),
        "txs_pool" to type(MoneroWalletBlocksResult::class.java),
        "txs_to_subaddress" to type(MoneroWalletBlocksResult::class.java),
        "wallet_version" to type(MoneroVersion::class.java),
    )

    private fun fixture(name: String) = ModelTestSupport.fixture("wallet", name)

    private fun <T> readFixture(name: String, type: Class<T>): T = MoneroJson.deserialize(fixture(name), type)

    private fun <T> readJson(json: String, type: Class<T>): T = MoneroJson.deserialize(json, type)

    // fixtures: what monero-c writes

    @Test
    fun fixtures_AreAllTested() {
        assertEquals(ModelTestSupport.fixtureNames("wallet"), fixtureTypes.keys.sorted())
    }

    @Test
    fun fixtures_AreJson_WithoutByteOrderMark() {
        for (name in fixtureTypes.keys) {
            val bytes = ModelTestSupport.fixtureFile("wallet", name).readBytes()
            assertFalse(bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte(), name)
            tree(String(bytes, Charsets.UTF_8))
        }
    }

    @TestFactory
    fun fixture_IsWrittenBackUnchanged() = fixtureTypes.map { (name, type) ->
        dynamicTest(name) {
            val json = fixture(name)
            assertSameJson(json, write(read(json, type)))
        }
    }

    @TestFactory
    fun fixture_IsWrittenBackUnchanged_AfterReadingTheWrittenJson() = fixtureTypes.map { (name, type) ->
        dynamicTest(name) {
            val json = fixture(name)
            val written = write(read(json, type))
            assertSameJson(json, write(read(written, type)))
        }
    }

    @Test
    fun syncResult_ReadsTheFixtures() {
        val result = readFixture("sync_result", MoneroSyncResult::class.java)
        assertEquals(100L, result.numBlocksFetched)
        assertEquals(true, result.receivedMoney)
    }

    @Test
    fun rpcConnection_ReadsTheFixture() {
        val connection = readFixture("rpc_connection", MoneroRpcConnection::class.java)
        assertTrue(connection.uri!!.startsWith("http://127"))
        assertEquals(0, connection.priority)
        assertEquals(false, connection.sslVerify)
        assertNull(connection.username)
    }

    @Test
    fun walletVersion_ReadsTheFixture() {
        val version = readFixture("wallet_version", MoneroVersion::class.java)
        assertEquals(65568, version.number)
        assertEquals(false, version.isRelease)
    }

    @Test
    fun account_WithSubaddresses_ReadsTheFixture() {
        val account = readFixture("account_with_subaddresses", MoneroAccount::class.java)
        assertEquals(0, account.index)
        assertEquals(BigInteger.valueOf(3518101685576566), account.balance)
        assertEquals(BigInteger.valueOf(1442502852058827), account.unlockedBalance)
        assertEquals(1, account.subaddresses!!.size)
        val subaddress = account.subaddresses!![0]
        assertEquals(0, subaddress.accountIndex)
        assertEquals(0, subaddress.index)
        assertEquals("Primary account", subaddress.label)
        assertEquals(true, subaddress.isUsed)
        assertEquals(100L, subaddress.numUnspentOutputs)
        assertEquals(59L, subaddress.numBlocksToUnlock)
        assertEquals(account.primaryAddress, subaddress.address)
    }

    @Test
    fun account_WithoutSubaddresses_HasNoListOfThem() {
        assertNull(readFixture("account_without_subaddresses", MoneroAccount::class.java).subaddresses)
    }

    @Test
    fun accounts_ReadTheFixture() {
        val accounts = MoneroJson.deserialize<List<MoneroAccount>>(fixture("accounts_with_subaddresses"))
        assertTrue(accounts.isNotEmpty())
        assertEquals(accounts.indices.toList(), accounts.map { it.index })
        assertTrue(accounts.all { it.subaddresses != null })
        assertTrue(MoneroJson.deserialize<List<MoneroAccount>>(fixture("accounts_without_subaddresses")).all { it.subaddresses == null })
    }

    @Test
    fun integratedAddress_ReadsTheFixtures() {
        val decoded = readFixture("integrated_address_decoded", MoneroIntegratedAddress::class.java)
        assertEquals("0123456789abcdef", decoded.paymentId)
        assertTrue(decoded.integratedAddress!!.startsWith("4CSNP3qpqW".take(2)))
        assertNotNull(decoded.standardAddress)
    }

    @Test
    fun messageSignatureResults_ReadTheFixtures() {
        val spend = readFixture("message_signature_spend", MoneroMessageSignatureResult::class.java)
        assertEquals(true, spend.isGood)
        assertEquals(false, spend.isOld)
        assertEquals(2, spend.version)
        assertEquals(MoneroMessageSignatureType.SIGN_WITH_SPEND_KEY, spend.signatureType)
        assertEquals(MoneroMessageSignatureType.SIGN_WITH_VIEW_KEY, readFixture("message_signature_view", MoneroMessageSignatureResult::class.java).signatureType)
        assertEquals(false, readFixture("message_signature_wrong_message", MoneroMessageSignatureResult::class.java).isGood)
    }

    @Test
    fun addressBookEntries_ReadTheFixture() {
        val entries = MoneroJson.deserialize<List<MoneroAddressBookEntry>>(fixture("address_book_entries"))
        assertEquals(listOf(0L, 1L), entries.map { it.index })
        assertEquals(listOf("friend", "my deposits"), entries.map { it.description })
        assertTrue(entries.all { it.paymentId == null })
    }

    @Test
    fun txs_OfMinerTxs_ReadTheFixture() {
        val result = readFixture("txs_miner", MoneroWalletBlocksResult::class.java)
        val block = result.blocks!![0]
        assertEquals(1L, block.height)
        val tx = block.txs!![0] as MoneroTxWallet
        assertSame(block, tx.block)
        assertEquals(true, tx.isMinerTx)
        assertEquals(true, tx.isIncoming)
        assertEquals(false, tx.isOutgoing)
        assertEquals(BigInteger.valueOf(61), tx.unlockTime)
        assertNull(tx.outgoingTransfer)

        val transfer = tx.incomingTransfers!![0]
        assertSame(tx, transfer.tx)
        assertEquals(BigInteger.valueOf(35184338534400), transfer.amount)
        assertEquals(0, transfer.accountIndex)
        assertEquals(0, transfer.subaddressIndex)
        assertEquals(2L, transfer.numSuggestedConfirmations)
        assertEquals(true, transfer.isIncoming)
        assertEquals(false, transfer.isOutgoing)
        assertEquals(BigInteger.valueOf(35184338534400), tx.getIncomingAmount())
        assertEquals(BigInteger.ZERO, tx.getOutgoingAmount())
        assertEquals(listOf<MoneroTransfer>(transfer), tx.getTransfers())
    }

    @Test
    fun tx_InThePool_IsInTheBlockWithoutAHeight() {
        val result = readFixture("txs_pool", MoneroWalletBlocksResult::class.java)
        val block = result.blocks!![0]
        assertNull(block.height)
        val tx = block.getTxsWallet()[0]
        assertNull(tx.height)
        assertEquals(true, tx.inTxPool)
        assertEquals(false, tx.isConfirmed)
        assertEquals(true, tx.isOutgoing)

        val transfer = tx.outgoingTransfer!!
        assertSame(tx, transfer.tx)
        assertEquals(BigInteger.valueOf(1000000000), transfer.amount)
        assertEquals(listOf(0), transfer.subaddressIndices)
        assertEquals(1, transfer.addresses!!.size)
        assertEquals(1, transfer.destinations!!.size)
        assertEquals(BigInteger.valueOf(1000000000), transfer.destinations!![0].amount)
        assertEquals(false, transfer.isIncoming)
        assertEquals(true, transfer.isOutgoing)
        assertEquals(BigInteger.valueOf(1000000000), tx.getOutgoingAmount())
        assertEquals(BigInteger.ZERO, tx.getIncomingAmount())
    }

    @Test
    fun tx_WhenMined_IsInABlockWithAHeight() {
        val tx = readFixture("tx_outgoing_confirmed", MoneroWalletBlocksResult::class.java).getTxs()[0]
        assertEquals(101L, tx.height)
        assertEquals(1791741219L, tx.block!!.timestamp)
        assertEquals(true, tx.isConfirmed)
        assertEquals(BigInteger.valueOf(2599200000), tx.fee)
    }

    @Test
    fun tx_ToASubaddress_HasTheDestinationsThatItPays() {
        val tx = readFixture("tx_to_subaddress", MoneroWalletBlocksResult::class.java).getTxs()[0]
        val destinations = tx.outgoingTransfer!!.destinations!!
        assertEquals(listOf(BigInteger.valueOf(50000000000), BigInteger.valueOf(700000000)), destinations.map { it.amount })
        assertEquals(BigInteger.valueOf(50700000000), tx.outgoingTransfer!!.amount)
    }

    @Test
    fun emptyResults_ReadAsEmptyLists() {
        val result = readFixture("txs_empty", MoneroWalletBlocksResult::class.java)
        assertEquals(emptyList(), result.blocks)
        assertEquals(emptyList(), result.getTxs())
        assertEquals(emptyList(), result.getTransfers())
        assertEquals(emptyList(), result.getOutputs())
        assertEquals(emptyList(), result.getInputs())
        assertEquals(emptyList(), readJson("{}", MoneroWalletBlocksResult::class.java).getTxs())
    }

    @Test
    fun outputs_ReadTheFixture() {
        val result = readFixture("outputs_miner", MoneroWalletBlocksResult::class.java)
        val outputs = result.getOutputs()
        assertTrue(outputs.isNotEmpty())
        val first = outputs[0]
        assertEquals(BigInteger.valueOf(35184338534400), first.amount)
        assertEquals(0L, first.index)
        assertEquals(0, first.accountIndex)
        assertEquals(0, first.subaddressIndex)
        assertEquals(false, first.isSpent)
        assertEquals(false, first.isFrozen)
        assertNotNull(first.keyImage!!.hex)
        assertNotNull(first.stealthPublicKey)

        val tx = first.getTxWallet()!!
        assertSame(tx, first.tx)
        assertEquals(1L, tx.height)
        assertEquals(outputs.size, result.getTxs().sumOf { it.getOutputsWallet().size })
        assertTrue(result.getInputs().isEmpty())
    }

    @Test
    fun outputs_FrozenAndSpent_ReadTheFixtures() {
        assertTrue(readFixture("outputs_frozen", MoneroWalletBlocksResult::class.java).getOutputs().any { it.isFrozen == true })
        assertTrue(readFixture("outputs_spent", MoneroWalletBlocksResult::class.java).getOutputs().any { it.isSpent == true })
    }

    @Test
    fun listenerNotifications_ReadTheFixtures() {
        val received = readFixture("listener_output_received_first", MoneroWalletBlocksResult::class.java)
        val output = received.getOutputs()[0]
        assertEquals(BigInteger.valueOf(35184338534400), output.amount)
        assertEquals(0, output.accountIndex)
        assertEquals(0, output.subaddressIndex)
        val tx = output.getTxWallet()!!
        assertEquals(1L, tx.height)
        assertEquals(2, tx.version)
        assertEquals(BigInteger.valueOf(61), tx.unlockTime)
        assertEquals(true, tx.isLocked)
        assertNotNull(tx.hash)
        for (name in listOf("listener_output_received_last", "listener_output_spent_first", "listener_output_spent_last")) {
            val result = readFixture(name, MoneroWalletBlocksResult::class.java)
            assertTrue(result.getTxs().size == 1, name)
        }
    }

    @Test
    fun txSet_OfACreatedTx_ReadsTheFixture() {
        val result = readFixture("tx_sets_created", MoneroTxSetsResult::class.java)
        assertEquals(1, result.txSets!!.size)
        val set = result.txSets!![0]
        val tx = set.txs!![0]
        assertSame(set, tx.txSet)
        assertEquals(listOf(tx), result.getTxs())
        assertEquals(BigInteger.valueOf(2599200000), tx.fee)
        assertEquals(16, tx.ringSize)
        assertEquals(false, tx.relay)
        assertEquals(false, tx.isRelayed)
        assertNotNull(tx.key)
        assertNotNull(tx.metadata)
        assertEquals(BigInteger.valueOf(1000000000), tx.outgoingTransfer!!.amount)
        assertEquals(2, tx.inputs!!.size)
        assertTrue(tx.inputs!!.all { it is MoneroOutputWallet && it.keyImage != null && it.tx === tx })
        assertNull(set.unsignedTxHex)
    }

    @Test
    fun txSets_OfManyTxsAndOfSweeps_ReadTheFixtures() {
        val many = readFixture("tx_sets_created_many", MoneroTxSetsResult::class.java).getTxs()
        assertEquals(1, many.size)
        assertEquals(listOf(BigInteger.valueOf(300000000), BigInteger.valueOf(400000000)), many[0].outgoingTransfer!!.destinations!!.map { it.amount })
        for (name in listOf("tx_sets_created_relayed", "tx_sets_swept_dust", "tx_sets_swept_output", "tx_sets_swept_unlocked")) {
            val result = readFixture(name, MoneroTxSetsResult::class.java)
            assertTrue(result.txSets!!.all { set -> set.txs!!.all { it.txSet === set } }, name)
        }
    }

    @Test
    fun txSet_OfAViewOnlyWallet_HasTheUnsignedHex() {
        val set = readFixture("tx_sets_unsigned", MoneroTxSetsResult::class.java).txSets!![0]
        assertTrue(set.unsignedTxHex!!.startsWith("4d6f6e65726f"))
        assertNull(set.signedTxHex)
        assertEquals(BigInteger.valueOf(200000000), set.txs!![0].outgoingTransfer!!.amount)
    }

    @Test
    fun txSet_Signed_HasTheSignedHexAndNoDetails() {
        val set = readFixture("tx_sets_signed", MoneroTxSetsResult::class.java).txSets!![0]
        assertTrue(set.signedTxHex!!.startsWith("4d6f6e65726f"))
        val tx = set.txs!![0]
        assertNotNull(tx.hash)
        assertNotNull(tx.key)
        assertNull(tx.fee)
        assertEquals(true, tx.isOutgoing)
    }

    @Test
    fun txSet_Described_HasTheDetailsOfTheTx() {
        val tx = readFixture("tx_sets_described", MoneroTxSetsResult::class.java).getTxs()[0]
        assertEquals(BigInteger.valueOf(70364919152761), tx.inputSum)
        assertEquals(BigInteger.valueOf(70362319952761), tx.outputSum)
        assertEquals(BigInteger.valueOf(70362119952761), tx.changeAmount)
        assertEquals(0, tx.numDummyOutputs)
        assertNotNull(tx.changeAddress)
        assertNotNull(tx.extraHex)
        assertEquals(listOf(3L, 53L), tx.getInputsWallet().map { it.index })
        assertEquals(BigInteger.valueOf(35184137208383), tx.getInputsWallet()[0].amount)
    }

    @Test
    fun keyImages_ReadTheFixtures() {
        val export = readFixture("key_images_export", MoneroKeyImageExportResult::class.java)
        assertEquals(0L, export.offset)
        assertEquals(117, export.keyImages!!.size)
        assertTrue(export.keyImages!!.all { it.hex != null && it.signature != null })
        val result = readFixture("key_images_import_result", MoneroKeyImageImportResult::class.java)
        assertNotNull(result.height)
        assertNotNull(result.spentAmount)
        assertNotNull(result.unspentAmount)
    }

    @Test
    fun checks_ReadTheFixtures() {
        val tx = readFixture("check_tx", MoneroCheckTx::class.java)
        assertEquals(true, tx.isGood)
        assertEquals(1L, tx.numConfirmations)
        assertEquals(BigInteger.valueOf(1000000000), tx.receivedAmount)
        assertEquals(false, tx.inTxPool)
        assertEquals(false, readFixture("check_tx_proof_bad", MoneroCheckTx::class.java).isGood)
        assertEquals(true, readFixture("check_tx_proof_good", MoneroCheckTx::class.java).isGood)
        assertEquals(true, readFixture("check_reserve", MoneroCheckReserve::class.java).isGood)
        assertNotNull(readFixture("check_reserve", MoneroCheckReserve::class.java).totalAmount)
        assertEquals(false, readFixture("check_reserve_bad", MoneroCheckReserve::class.java).isGood)
    }

    @Test
    fun multisig_ReadsTheFixtures() {
        val ready = readFixture("multisig_info_ready", MoneroMultisigInfo::class.java)
        assertEquals(true, ready.isMultisig)
        assertEquals(true, ready.isReady)
        assertEquals(2, ready.threshold)
        assertEquals(2, ready.numParticipants)
        assertEquals(false, readFixture("multisig_info_none", MoneroMultisigInfo::class.java).isMultisig)
        assertNotNull(readFixture("multisig_exchange_result_1", MoneroMultisigInitResult::class.java).multisigHex)
        assertNotNull(readFixture("multisig_exchange_result_1", MoneroMultisigInitResult::class.java).address)
    }

    @Test
    fun txConfig_OfAPaymentUri_ReadsTheFixture() {
        val config = readFixture("tx_config_from_uri", MoneroTxConfig::class.java)
        assertEquals("for lunch", config.note)
        assertEquals("Bob", config.recipientName)
        assertEquals(1, config.destinations!!.size)
        assertEquals(BigInteger.valueOf(1500000000), config.amount)
        assertNotNull(config.address)
    }

    @Test
    fun listsOfText_ReadTheFixtures() {
        val languages = MoneroJson.deserialize<List<String>>(fixture("seed_languages"))
        assertTrue(languages.contains("English"))
        assertEquals(12, languages.size)
        assertTrue(MoneroJson.deserialize<List<String>>(fixture("submitted_hashes")).all { it.length == 64 })
    }

    // configs and queries: what a caller writes and monero-c reads

    @Test
    fun walletConfig_WithEveryField_IsReadAndWritten() {
        val json = """{"path":"wallet","password":"pw","networkType":2,"server":{"uri":"http://localhost:38081","username":"u","password":"p"},
            "isTrustedDaemon":true,"seed":"hefty value","seedOffset":"off","primaryAddress":"$address","privateViewKey":"v",
            "privateSpendKey":"s","restoreHeight":573936,"language":"English","saveCurrent":false,"accountLookahead":2,"subaddressLookahead":20,
            "isMultisig":false,"regtest":true}"""
        val config = readJson(json, MoneroWalletConfig::class.java)
        assertEquals(MoneroNetworkType.STAGENET, config.networkType)
        assertEquals("http://localhost:38081", config.server!!.uri)
        assertEquals(573936L, config.restoreHeight)
        assertSameJson(json, write(config))
    }

    @Test
    fun walletConfig_OfARegtestWallet_IsWhatTheIntegrationTestsOfMoneroCSend() {
        val json = """{"path":"w","password":"p","networkType":0,"server":{"uri":"http://127.0.0.1:18081"},"regtest":true,"restoreHeight":0}"""
        assertSameJson(json, write(readJson(json, MoneroWalletConfig::class.java)))
    }

    @Test
    fun txConfig_WithEveryField_IsReadAndWritten() {
        val json = """{"destinations":[{"address":"a","amount":1},{"address":"b","amount":2}],"subtractFeeFrom":[0,1],"paymentId":"p",
            "priority":2,"ringSize":16,"fee":3,"accountIndex":1,"subaddressIndices":[0,2],"canSplit":true,"relay":false,"note":"n",
            "recipientName":"r","belowAmount":4,"sweepEachSubaddress":true,"keyImage":"k"}"""
        val config = readJson(json, MoneroTxConfig::class.java)
        assertEquals(MoneroTxPriority.NORMAL, config.priority)
        assertEquals(listOf(0, 1), config.subtractFeeFrom)
        assertSameJson(json, write(config))
    }

    @Test
    fun txConfig_Priority_IsTheNumber() {
        val expected = listOf(0 to MoneroTxPriority.DEFAULT, 1 to MoneroTxPriority.UNIMPORTANT, 2 to MoneroTxPriority.NORMAL, 3 to MoneroTxPriority.ELEVATED)
        for ((number, priority) in expected) {
            val config = readJson("""{"priority":$number}""", MoneroTxConfig::class.java)
            assertEquals(priority, config.priority)
            assertEquals("""{"priority":$number}""", write(config))
        }
    }

    @Test
    fun txConfig_WithOneDestination_HasItsAddressAndAmount() {
        val config = readJson("""{"destinations":[{"address":"a","amount":5}]}""", MoneroTxConfig::class.java)
        assertEquals("a", config.address)
        assertEquals(BigInteger.valueOf(5), config.amount)
    }

    @Test
    fun txConfig_SettingTheAddressAndTheAmount_MakesTheDestination() {
        val config = MoneroTxConfig()
        assertNull(config.address)
        config.address = "a"
        config.amount = BigInteger.TEN
        assertSameJson("""{"destinations":[{"address":"a","amount":10}]}""", write(config))

        config.address = "b"
        assertSameJson("""{"destinations":[{"address":"b","amount":10}]}""", write(config))
    }

    @Test
    fun txConfig_AddDestination_ChainsAndKeepsTheOrder() {
        val config = MoneroTxConfig().addDestination("a", BigInteger.ONE).addDestination("b")
        assertSameJson("""{"destinations":[{"address":"a","amount":1},{"address":"b"}]}""", write(config))
    }

    @Test
    fun txConfig_WithManyDestinations_RefusesAnAddressAndAnAmount() {
        val config = MoneroTxConfig().addDestination("a", BigInteger.ONE).addDestination("b", BigInteger.TWO)
        assertNull(config.address)
        assertNull(config.amount)
        assertFailsWith<IllegalStateException> { config.address = "c" }
        assertFailsWith<IllegalStateException> { config.amount = BigInteger.TEN }
        assertEquals(2, config.destinations!!.size)
    }

    @Test
    fun txQuery_WithEveryField_IsReadAndWritten() {
        val json = """{"hashes":["a","b"],"hasPaymentId":true,"paymentIds":["p"],"height":5,"minHeight":1,"maxHeight":9,"includeOutputs":true,
            "isConfirmed":true,"inTxPool":false,"isIncoming":true,"isOutgoing":false,"isLocked":false,"isMinerTx":false,
            "transferQuery":{"isIncoming":true,"accountIndex":1,"txQuery":{"isConfirmed":true}},
            "inputQuery":{"isSpent":true,"minAmount":1},"outputQuery":{"accountIndex":0,"maxAmount":10}}"""
        val query = readJson(json, MoneroTxQuery::class.java)
        assertEquals(5L, query.height)
        assertEquals(listOf("a", "b"), query.hashes)
        assertEquals(true, query.transferQuery!!.isIncoming)
        assertEquals(true, query.transferQuery!!.txQuery!!.isConfirmed)
        assertEquals(BigInteger.ONE, query.inputQuery!!.minAmount)
        assertSameJson(json, write(query))
    }

    @Test
    fun txQuery_ThatMoneroCTestsSend_IsWrittenBackUnchanged() {
        for (json in listOf("""{}""", """{"isConfirmed":true,"minHeight":50}""", """{"hash":"abc"}""", """{"isOutgoing":true,"transferQuery":{"hasDestinations":true}}""",
            """{"outputQuery":{"isSpent":false},"includeOutputs":true}""")) {
            assertSameJson(json, write(readJson(json, MoneroTxQuery::class.java)))
        }
    }

    @Test
    fun transferQuery_ThatMoneroCTestsSend_IsWrittenBackUnchanged() {
        for (json in listOf("""{"isIncoming":true,"accountIndex":1}""", """{"isIncoming":false,"destinations":[{"address":"a","amount":1}]}""",
            """{"isIncoming":true,"txQuery":{"isConfirmed":true,"minHeight":50}}""", """{"address":"a","subaddressIndex":2}""")) {
            assertSameJson(json, write(readJson(json, MoneroTransferQuery::class.java)))
        }
    }

    @Test
    fun outputQuery_ThatMoneroCTestsSend_IsWrittenBackUnchanged() {
        for (json in listOf("""{"isSpent":false}""", """{"accountIndex":0,"subaddressIndices":[0,1],"minAmount":5,"maxAmount":10}""",
            """{"isFrozen":true,"txQuery":{"isLocked":true}}""", """{"keyImage":{"hex":"k"}}""")) {
            assertSameJson(json, write(readJson(json, MoneroOutputQuery::class.java)))
        }
    }

    @Test
    fun transferQuery_HoldsTheQueryOfItsTx() {
        val query = readJson("""{"isIncoming":true,"txQuery":{"isConfirmed":true,"minHeight":50}}""", MoneroTransferQuery::class.java)
        assertEquals(true, query.isIncoming)
        assertEquals(false, query.isOutgoing)
        assertEquals(50L, query.txQuery!!.minHeight)
    }

    @Test
    fun transferQuery_ThatAsksForNeither_HasNoDirection() {
        val query = readJson("""{"accountIndex":1}""", MoneroTransferQuery::class.java)
        assertNull(query.isIncoming)
        assertNull(query.isOutgoing)
        assertEquals("""{"accountIndex":1}""", write(query))
    }

    @Test
    fun transferQuery_WithDestinations_IsWritten() {
        val query = MoneroTransferQuery()
        query.destinations = listOf(MoneroDestination("a", BigInteger.ONE), MoneroDestination("b"))
        query.hasDestinations = true
        assertSameJson("""{"destinations":[{"address":"a","amount":1},{"address":"b"}],"hasDestinations":true}""", write(query))
    }

    // the rest of the models

    @Test
    fun subaddress_WithEveryField_IsReadAndWritten() {
        val json = """{"accountIndex":1,"index":2,"address":"a","label":"l","balance":3,"unlockedBalance":4,"numUnspentOutputs":5,"isUsed":true,"numBlocksToUnlock":6}"""
        assertSameJson(json, write(readJson(json, MoneroSubaddress::class.java)))
    }

    @Test
    fun account_WithATag_IsReadAndWritten() {
        val json = """{"index":1,"primaryAddress":"a","balance":2,"unlockedBalance":3,"tag":"savings"}"""
        val account = readJson(json, MoneroAccount::class.java)
        assertEquals("savings", account.tag)
        assertSameJson(json, write(account))
    }

    @Test
    fun accountTags_AreReadAndWritten() {
        val json = """[{"tag":"savings","label":"Savings","accountIndices":[0,2]},{"tag":"empty","accountIndices":[]}]"""
        val tags = MoneroJson.deserialize<List<MoneroAccountTag>>(json)
        assertEquals(listOf(0, 2), tags[0].accountIndices)
        assertSameJson(json, write(tags))
    }

    @Test
    fun decodedAddress_IsReadAndWritten_WithTheNumbers() {
        for ((number, type) in MoneroAddressType.entries.withIndex()) {
            val json = """{"address":"a","addressType":$number,"networkType":1}"""
            val decoded = readJson(json, MoneroDecodedAddress::class.java)
            assertEquals(type, decoded.addressType)
            assertEquals(MoneroNetworkType.TESTNET, decoded.networkType)
            assertSameJson(json, write(decoded))
        }
        assertEquals(listOf("PRIMARY_ADDRESS", "INTEGRATED_ADDRESS", "SUBADDRESS"), MoneroAddressType.entries.map { it.name })
    }

    @Test
    fun multisigSignResult_IsReadAndWritten() {
        val json = """{"signedMultisigTxHex":"abc","txHashes":["a","b"]}"""
        assertSameJson(json, write(readJson(json, MoneroMultisigSignResult::class.java)))
    }

    @Test
    fun txSet_WithEveryHex_IsReadAndWritten() {
        val json = """{"txs":[{"hash":"a"}],"signedTxHex":"s","unsignedTxHex":"u","multisigTxHex":"m"}"""
        val set = readJson(json, MoneroTxSet::class.java)
        assertEquals("u", set.unsignedTxHex)
        assertSameJson(json, write(set))
    }

    @Test
    fun tx_OfAWallet_WithEveryFieldOfItsOwn_IsReadAndWritten() {
        val json = """{"hash":"h","isIncoming":true,"isOutgoing":true,"note":"n","inputSum":1,"outputSum":2,"changeAddress":"c","changeAmount":3,
            "numDummyOutputs":4,"extraHex":"e","incomingTransfers":[{"amount":5,"accountIndex":0,"subaddressIndex":1,"address":"a","numSuggestedConfirmations":10}],
            "outgoingTransfer":{"amount":6,"accountIndex":0,"subaddressIndices":[0],"addresses":["x"],"destinations":[{"address":"d","amount":6}]},
            "inputs":[{"amount":7,"accountIndex":0,"isSpent":true}],"outputs":[{"amount":8,"subaddressIndex":2,"isFrozen":false}]}"""
        val tx = readJson(json, MoneroTxWallet::class.java)
        assertSame(tx, tx.incomingTransfers!![0].tx)
        assertSame(tx, tx.outgoingTransfer!!.tx)
        assertSameJson(json, write(tx))
    }

    @Test
    fun blocksOfAWallet_HaveTheTxsOfAWallet_AndTheBlocksOfTheDaemonDoNot() {
        val json = """{"height":3,"txs":[{"hash":"a","isIncoming":true,"incomingTransfers":[{"amount":1}]}]}"""
        val wallet = readJson(json, MoneroBlockWallet::class.java)
        assertTrue(wallet.txs!![0] is MoneroTxWallet)
        assertEquals(1, wallet.getTxsWallet().size)

        val daemon = readJson(json, MoneroBlock::class.java)
        assertTrue(daemon.txs!![0] !is MoneroTxWallet)
        assertEquals(MoneroTx::class.java, daemon.txs!![0].javaClass)
    }

    @Test
    fun txOfAWallet_ReadsItsInputsAndOutputs_AsWalletOutputs() {
        val tx = readJson("""{"inputs":[{"accountIndex":1,"isSpent":true}],"outputs":[{"subaddressIndex":2,"isFrozen":true}]}""", MoneroTxWallet::class.java)
        assertEquals(MoneroOutputWallet::class, tx.inputs!![0]::class)
        assertEquals(true, tx.getInputsWallet()[0].isSpent)
        assertEquals(2, tx.getOutputsWallet()[0].subaddressIndex)
        assertSame(tx, tx.getInputsWallet()[0].tx)

        val daemon = readJson("""{"inputs":[{"amount":1}]}""", MoneroTx::class.java)
        assertEquals(MoneroOutput::class.java, daemon.inputs!![0].javaClass)
    }

    @Test
    fun txOfAWallet_WritesItsOutputs_AsTheClassesThatTheyAre() {
        val tx = MoneroTxWallet()
        val output = MoneroOutputWallet()
        output.accountIndex = 3
        output.amount = BigInteger.ONE
        tx.outputs = listOf(output)
        assertSameJson("""{"outputs":[{"amount":1,"accountIndex":3}]}""", write(tx))
    }

    @Test
    fun jsonThatIsNotValid_IsRejected() {
        for (json in listOf("""{"outputs":{"amount":1}}""", """{"outputs":[1]}""", """{"incomingTransfers":[[]]}""", """{"inputs":"x"}""")) {
            assertFailsWith<MoneroError>(json) { readJson(json, MoneroTxWallet::class.java) }
        }
        for (json in listOf("""{"txs":{}}""", """{"txs":[1]}""")) {
            assertFailsWith<MoneroError>(json) { readJson(json, MoneroBlockWallet::class.java) }
        }
        for (json in listOf("""{"blocks":{}}""", """{"blocks":[1]}""", """{"blocks":[{"txs":[[]]}]}""")) {
            assertFailsWith<MoneroError>(json) { readJson(json, MoneroWalletBlocksResult::class.java) }
        }
    }

    // results with their txs

    @Test
    fun walletBlocksResult_GetTxs_ListsTheTxsInTheOrderOfTheBlocks() {
        val json = """{"blocks":[{"height":1,"txs":[{"hash":"a","outgoingTransfer":{"amount":1},"outputs":[{"amount":10}],"inputs":[{"amount":20}]},{"hash":"b"}]},
            {"txs":[{"hash":"c","incomingTransfers":[{"amount":2},{"amount":3}],"outputs":[{"amount":30}]}]}]}"""
        val result = readJson(json, MoneroWalletBlocksResult::class.java)
        assertEquals(listOf("a", "b", "c"), result.getTxs().map { it.hash })
        assertEquals(listOf(1L, 1L, null), result.getTxs().map { it.height })
        assertEquals(listOf(1, 2, 3), result.getTransfers().map { it.amount!!.toInt() })
        assertEquals(listOf(10, 30), result.getOutputs().map { it.amount!!.toInt() })
        assertEquals(listOf(20), result.getInputs().map { it.amount!!.toInt() })
    }

    @Test
    fun txSetsResult_GetTxs_ListsTheTxsInTheOrderOfTheSets() {
        val result = readJson("""{"txSets":[{"txs":[{"hash":"a"},{"hash":"b"}]},{"signedTxHex":"s"},{"txs":[{"hash":"c"}]}]}""", MoneroTxSetsResult::class.java)
        assertEquals(listOf("a", "b", "c"), result.getTxs().map { it.hash })
        assertEquals(emptyList(), readJson("{}", MoneroTxSetsResult::class.java).getTxs())
    }

    // parents

    @Test
    fun tx_SetsItselfAsTheTxOfItsTransfers() {
        val tx = MoneroTxWallet()
        val incoming = MoneroIncomingTransfer()
        val outgoing = MoneroOutgoingTransfer()
        tx.incomingTransfers = listOf(incoming)
        tx.outgoingTransfer = outgoing
        assertSame(tx, incoming.tx)
        assertSame(tx, outgoing.tx)
        assertEquals(listOf<MoneroTransfer>(outgoing, incoming), tx.getTransfers())
    }

    @Test
    fun set_SetsItselfAsTheSetOfItsTxs() {
        val set = MoneroTxSet()
        val tx = MoneroTxWallet()
        set.txs = listOf(tx)
        assertSame(set, tx.txSet)
    }

    @Test
    fun block_OfAWallet_SetsItselfAsTheBlockOfItsTxs() {
        val block = MoneroBlockWallet()
        val tx = MoneroTxWallet()
        block.txs = listOf(tx)
        assertSame(block, tx.block)
    }

    @Test
    fun parents_AreSetByTheDeserializers_WhateverTheOrderOfTheFields() {
        val json = """{"txs":[{"outgoingTransfer":{"amount":1},"incomingTransfers":[{"amount":2}],"outputs":[{"amount":3}],"hash":"a"}],"height":9}"""
        val block = readJson(json, MoneroBlockWallet::class.java)
        val tx = block.getTxsWallet()[0]
        assertSame(block, tx.block)
        assertEquals(9L, tx.height)
        assertSame(tx, tx.outgoingTransfer!!.tx)
        assertSame(tx, tx.incomingTransfers!![0].tx)
        assertSame(tx, tx.getOutputsWallet()[0].tx)

        val set = readJson("""{"txs":[{"hash":"a"}],"unsignedTxHex":"u"}""", MoneroTxSet::class.java)
        assertSame(set, set.txs!![0].txSet)
    }

    @Test
    fun parents_AreNotInTheJson_SoAGraphIsWrittenWithoutCycles() {
        val set = MoneroTxSet()
        val tx = MoneroTxWallet()
        val block = MoneroBlockWallet()
        val transfer = MoneroIncomingTransfer()
        transfer.amount = BigInteger.ONE
        tx.hash = "a"
        tx.incomingTransfers = listOf(transfer)
        set.txs = listOf(tx)
        block.txs = listOf(tx)
        assertSame(set, tx.txSet)
        assertSame(block, tx.block)
        assertSameJson("""{"txs":[{"hash":"a","incomingTransfers":[{"amount":1}]}]}""", write(set))
        assertSameJson("""{"txs":[{"hash":"a","incomingTransfers":[{"amount":1}]}]}""", write(block))
    }

    @Test
    fun tx_WhenItsListsAreReplacedOrCleared_IsStillSafe() {
        val tx = MoneroTxWallet()
        tx.incomingTransfers = listOf(MoneroIncomingTransfer())
        tx.incomingTransfers = null
        tx.outgoingTransfer = MoneroOutgoingTransfer()
        tx.outgoingTransfer = null
        tx.inputs = null
        tx.outputs = emptyList()
        assertEquals(emptyList(), tx.getTransfers())
        assertEquals(emptyList(), tx.getInputsWallet())
        assertEquals(emptyList(), tx.getOutputsWallet())
        assertEquals(BigInteger.ZERO, tx.getIncomingAmount())
        assertEquals(BigInteger.ZERO, tx.getOutgoingAmount())
    }

    @Test
    fun blockWithManyTxs_IsReadAndWritten() {
        val txs = (0 until 1000).joinToString(",") { """{"hash":"tx$it","isIncoming":true,"incomingTransfers":[{"amount":$it,"accountIndex":0}]}""" }
        val json = """{"blocks":[{"height":5,"txs":[$txs]}]}"""
        val result = readJson(json, MoneroWalletBlocksResult::class.java)
        assertEquals(1000, result.getTxs().size)
        assertEquals(1000, result.getTransfers().size)
        assertSameJson(json, write(result))
    }

    // copies

    @Test
    fun copy_OfATx_IsDeep_AndHasItsOwnTransfersAndOutputs() {
        val tx = readFixture("txs_miner", MoneroWalletBlocksResult::class.java).getTxs()[0]
        val copy = tx.copy()
        assertNull(copy.block)
        assertNotSame(tx.incomingTransfers!![0], copy.incomingTransfers!![0])
        assertSame(copy, copy.incomingTransfers!![0].tx)
        assertSame(tx, tx.incomingTransfers!![0].tx)
        assertSameJson(write(tx), write(copy))

        val sent = readFixture("tx_outgoing_confirmed", MoneroWalletBlocksResult::class.java).getTxs()[0].copy()
        assertSame(sent, sent.outgoingTransfer!!.tx)
        assertNotSame(sent.outgoingTransfer!!.destinations!![0], sent.copy().outgoingTransfer!!.destinations!![0])

        val withOutputs = readFixture("outputs_miner", MoneroWalletBlocksResult::class.java).getTxs()[0].copy()
        assertTrue(withOutputs.outputs!!.all { it is MoneroOutputWallet && it.tx === withOutputs })
    }

    @Test
    fun copy_OfASet_HasItsOwnTxs() {
        val set = readFixture("tx_sets_created", MoneroTxSetsResult::class.java).txSets!![0]
        val copy = set.copy()
        assertNotSame(set.txs!![0], copy.txs!![0])
        assertSame(copy, copy.txs!![0].txSet)
        assertSame(set, set.txs!![0].txSet)
        assertSameJson(write(set), write(copy))
    }

    @Test
    fun copy_OfABlock_HasItsOwnTxsOfAWallet() {
        val block = readFixture("txs_miner", MoneroWalletBlocksResult::class.java).blocks!![0]
        val copy = block.copy()
        assertTrue(copy.txs!![0] is MoneroTxWallet)
        assertSame(copy, copy.txs!![0].block)
        assertSame(block, block.txs!![0].block)
        assertNotSame(block.txs!![0], copy.txs!![0])
        assertSameJson(write(block), write(copy))
    }

    @Test
    fun copy_OfAQuery_CopiesTheQueriesInsideIt() {
        val query = readJson("""{"hashes":["a"],"transferQuery":{"isIncoming":true,"txQuery":{"minHeight":1}},"outputQuery":{"minAmount":5,"txQuery":{"hashes":["b"]}}}""", MoneroTxQuery::class.java)
        val copy = query.copy()
        assertNotSame(query.transferQuery, copy.transferQuery)
        assertNotSame(query.transferQuery!!.txQuery, copy.transferQuery!!.txQuery)
        assertNotSame(query.outputQuery!!.txQuery, copy.outputQuery!!.txQuery)
        assertNotSame(query.hashes, copy.hashes)
        assertSameJson(write(query), write(copy))
    }

    @Test
    fun copy_IsOfTheSameClass_WhateverTheTypeItIsCalledOn() {
        val tx: MoneroTx = MoneroTxWallet()
        assertEquals(MoneroTxWallet::class, tx.copy()::class)
        val wallet: MoneroTxWallet = MoneroTxQuery()
        assertEquals(MoneroTxQuery::class, wallet.copy()::class)
        val output: MoneroOutputWallet = MoneroOutputQuery()
        assertEquals(MoneroOutputQuery::class, output.copy()::class)
        val transfer: MoneroTransfer = MoneroTransferQuery()
        assertEquals(MoneroTransferQuery::class, transfer.copy()::class)
        val block: MoneroBlock = MoneroBlockWallet()
        assertEquals(MoneroBlockWallet::class, block.copy()::class)
    }

    // enumerations, numbers and listeners

    @Test
    fun enumerations_HaveTheNumbersOfMoneroCpp() {
        assertEquals(listOf("DEFAULT", "UNIMPORTANT", "NORMAL", "ELEVATED"), MoneroTxPriority.entries.map { it.name })
        assertEquals(listOf("SIGN_WITH_SPEND_KEY", "SIGN_WITH_VIEW_KEY"), MoneroMessageSignatureType.entries.map { it.name })
    }

    @Test
    fun messageSignatureType_IsTheTextThatTheResultHas() {
        for ((text, type) in listOf("spend" to MoneroMessageSignatureType.SIGN_WITH_SPEND_KEY, "view" to MoneroMessageSignatureType.SIGN_WITH_VIEW_KEY)) {
            val result = readJson("""{"signatureType":"$text"}""", MoneroMessageSignatureResult::class.java)
            assertEquals(type, result.signatureType)
            assertEquals("""{"signatureType":"$text"}""", write(result))
        }
    }

    @Test
    fun messageSignatureType_ThatIsNotSpendOrView_IsRejected() {
        for (json in listOf("""{"signatureType":"other"}""", """{"signatureType":""}""", """{"signatureType":0}""", """{"signatureType":[]}""")) {
            assertFailsWith<MoneroError>(json) { readJson(json, MoneroMessageSignatureResult::class.java) }
        }
    }

    @Test
    fun numbers_UseTheWholeRangeOfTheirType() {
        val max = two64.subtract(BigInteger.ONE)
        val json = """{"amount":$max,"accountIndex":${Int.MAX_VALUE}}"""
        val transfer = readJson(json, MoneroIncomingTransfer::class.java)
        assertEquals(max, transfer.amount)
        assertEquals(Int.MAX_VALUE, transfer.accountIndex)
        assertSameJson(json, write(transfer))
    }

    @Test
    fun text_WithEscapesAndUnicode_SurvivesTheRoundTrip() {
        val tx = MoneroTxWallet()
        tx.note = "quote \" backslash \\ newline \n unicode é中😀"
        assertEquals(tx.note, readJson(write(tx), MoneroTxWallet::class.java).note)
    }

    @Test
    fun emptyText_IsAValue_NotAFieldThatIsNotSet() {
        val tx = MoneroTxWallet()
        tx.note = ""
        assertEquals("""{"note":""}""", write(tx))
        assertEquals("", readJson("""{"note":""}""", MoneroTxWallet::class.java).note)
    }

    @Test
    fun listener_ThatOverridesNothing_DoesNothing() {
        val listener = MoneroWalletListener()
        listener.onSyncProgress(1, 0, 2, 50.0, "half")
        listener.onNewBlock(1)
        listener.onBalancesChanged(BigInteger.ONE, BigInteger.ZERO)
        listener.onOutputReceived(MoneroOutputWallet())
        listener.onOutputSpent(MoneroOutputWallet())
    }

    @Test
    fun listener_GetsTheOutputsOfTheNotifications_InTheirTxAndBlock() {
        val output = readFixture("listener_output_received_first", MoneroWalletBlocksResult::class.java).getOutputs()[0]
        var received: MoneroOutputWallet? = null
        val listener = object : MoneroWalletListener() {
            override fun onOutputReceived(output: MoneroOutputWallet) {
                received = output
            }
        }
        listener.onOutputReceived(output)
        assertSame(output, received)
        assertEquals(1L, received!!.getTxWallet()!!.height)
    }
}
