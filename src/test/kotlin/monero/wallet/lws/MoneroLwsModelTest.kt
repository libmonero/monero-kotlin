package monero.wallet.lws

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.ModelTestSupport
import monero.ModelTestSupport.assertSameJson
import monero.ModelTestSupport.fixtureNames
import monero.ModelTestSupport.write
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.wallet.lws.wire.MoneroLwsAddressInfoResponse
import monero.wallet.lws.wire.MoneroLwsAddressRequest
import monero.wallet.lws.wire.MoneroLwsAddressTxsResponse
import monero.wallet.lws.wire.MoneroLwsLoginRequest
import monero.wallet.lws.wire.MoneroLwsLoginResponse
import monero.wallet.lws.wire.MoneroLwsSpend
import monero.wallet.lws.wire.MoneroLwsSubmitRawTxRequest
import monero.wallet.lws.wire.MoneroLwsSubmitRawTxResponse
import monero.wallet.lws.wire.MoneroLwsVersion
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

// the lws wire models against the json of a real monero-lws (52cf3fe)
// on a regtest node, one capture per endpoint in the fixtures. the
// captures are the contract: the models read them, and write them back
// unchanged. what no capture has (a mempool tx, a spend) is written by
// hand here, in the same shape. every test runs without monero-c, a
// server or the network
class MoneroLwsModelTest {

    /**
     * The fixtures and the type of the model that every one of them holds.
     */
    private val fixtureTypes = mapOf(
        "address_info" to MoneroLwsAddressInfoResponse::class.java,
        "address_info_after_blocks" to MoneroLwsAddressInfoResponse::class.java,
        "address_txs" to MoneroLwsAddressTxsResponse::class.java,
        "address_txs_after_blocks" to MoneroLwsAddressTxsResponse::class.java,
        "login" to MoneroLwsLoginResponse::class.java,
        "login_existing" to MoneroLwsLoginResponse::class.java,
        "version" to MoneroLwsVersion::class.java,
    )

    private fun fixture(name: String) = ModelTestSupport.fixture("lws", name)

    private fun <T> readFixture(name: String, type: Class<T>): T = MoneroJson.deserialize(fixture(name), type)

    // fixtures: what the server sends

    @Test
    fun fixtures_AreAllTested() {
        assertEquals(fixtureNames("lws"), fixtureTypes.keys.sorted())
    }

    @Test
    fun fixtures_AreJson_WithoutByteOrderMark() {
        for (name in fixtureTypes.keys) {
            val bytes = ModelTestSupport.fixtureFile("lws", name).readBytes()
            assertFalse(bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte(), name)
            ModelTestSupport.tree(String(bytes, Charsets.UTF_8))
        }
    }

    @TestFactory
    fun fixture_IsWrittenBackUnchanged() = fixtureTypes.map { (name, type) ->
        dynamicTest(name) {
            assertSameJson(fixture(name), write(MoneroJson.deserialize(fixture(name), type)))
        }
    }

    @TestFactory
    fun fixture_IsWrittenBackUnchanged_AfterReadingTheWrittenJson() = fixtureTypes.map { (name, type) ->
        dynamicTest(name) {
            val written = write(MoneroJson.deserialize(fixture(name), type))
            assertSameJson(fixture(name), write(MoneroJson.deserialize(written, type)))
        }
    }

    // login

    @Test
    fun loginResponse_ReadsTheCaptures() {
        val fresh = readFixture("login", MoneroLwsLoginResponse::class.java)
        assertEquals(true, fresh.isNewAddress)
        assertEquals(false, fresh.generatedLocally)
        assertEquals(0L, fresh.lookahead!!.majorIndex)
        assertEquals(0L, fresh.lookahead!!.minorIndex)

        val existing = readFixture("login_existing", MoneroLwsLoginResponse::class.java)
        assertEquals(false, existing.isNewAddress)
        assertEquals(0L, existing.lookahead!!.majorIndex)
    }

    @Test
    fun loginResponse_WithOnlyNewAddress_HasNothingElseSet() {
        val response = MoneroJson.deserialize("""{"new_address":true}""", MoneroLwsLoginResponse::class.java)
        assertEquals(true, response.isNewAddress)
        assertNull(response.generatedLocally)
        assertNull(response.lookahead)
    }

    @Test
    fun loginResponse_IgnoresFieldsTheCapturesDoNotHave() {
        // the spec says the server can add fields, the captures just
        // dont have any yet
        val response = MoneroJson.deserialize("""{"new_address":true,"from_the_future":{"a":[1]}}""", MoneroLwsLoginResponse::class.java)
        assertEquals(true, response.isNewAddress)
    }

    @Test
    fun loginRequest_WritesTheNamesOfTheWire() {
        val request = MoneroLwsLoginRequest()
        request.address = "4Abc"
        request.viewKey = "b2f5a1"
        request.createAccount = true
        request.generatedLocally = true
        assertSameJson("""{"address":"4Abc","view_key":"b2f5a1","create_account":true,"generated_locally":true}""", write(request))
    }

    // get_address_info

    @Test
    fun addressInfoResponse_ReadsTheCaptureOfAFreshAddress() {
        val info = readFixture("address_info", MoneroLwsAddressInfoResponse::class.java)
        assertEquals(BigInteger.ZERO, info.lockedFunds)
        assertEquals(BigInteger.ZERO, info.totalReceived)
        assertEquals(BigInteger.ZERO, info.totalSent)
        assertEquals(3707000L, info.scannedHeight)
        assertEquals(3707000L, info.scannedBlockHeight)
        assertEquals(3707000L, info.startHeight)
        assertNull(info.lookahead)
        assertNull(info.lookaheadFail)
        assertNull(info.spentOutputs)
    }

    @Test
    fun addressInfoResponse_ReadsTheCaptureAfterBlocks() {
        val info = readFixture("address_info_after_blocks", MoneroLwsAddressInfoResponse::class.java)
        assertEquals(BigInteger.valueOf(175921021585277), info.lockedFunds)
        assertEquals(BigInteger.valueOf(175921021585277), info.totalReceived)
        assertEquals(BigInteger.ZERO, info.totalSent)
        assertEquals(5L, info.scannedHeight)
        assertEquals(0L, info.startHeight)
    }

    @Test
    fun addressRequest_WritesTheNamesOfTheWire() {
        val request = MoneroLwsAddressRequest()
        request.address = "4Abc"
        request.viewKey = "b2f5a1"
        assertSameJson("""{"address":"4Abc","view_key":"b2f5a1"}""", write(request))
    }

    // get_address_txs

    @Test
    fun addressTxsResponse_ReadsTheCaptureOfAFreshAddress() {
        val txs = readFixture("address_txs", MoneroLwsAddressTxsResponse::class.java)
        assertNull(txs.transactions)
        assertEquals(3707000L, txs.transactionHeight)
    }

    @Test
    fun addressTxsResponse_ReadsTheCaptureAfterBlocks() {
        val txs = readFixture("address_txs_after_blocks", MoneroLwsAddressTxsResponse::class.java)
        assertEquals(5, txs.transactions!!.size)
        assertEquals(5L, txs.transactionHeight)
        assertEquals(BigInteger.valueOf(175921021585277), txs.totalReceived)

        val first = txs.transactions!![0]
        assertEquals(0L, first.id)
        assertEquals("08276167cf4492161019e93fdb7b5029a29ef5022d4723a2ef185fbfc7aa201c", first.hash)
        assertEquals("2026-10-22T11:29:31Z", first.timestamp)
        assertEquals(BigInteger.valueOf(35184338534400), first.totalReceived)
        assertEquals(BigInteger.ZERO, first.totalSent)
        assertEquals(BigInteger.ZERO, first.fee)
        assertEquals(BigInteger.valueOf(61), first.unlockTime)
        assertEquals(1L, first.height)
        assertEquals(true, first.isCoinbase)
        assertEquals(false, first.isMempool)
        assertEquals(0L, first.mixin)
        assertEquals(0L, first.recipient!!.majorIndex)
        assertEquals(0L, first.recipient!!.minorIndex)
        assertNull(first.spentOutputs)
    }

    @Test
    fun tx_InMempool_HasNoHeightAndNoTimestamp() {
        // synthetic, no capture has a mempool tx in it
        val json = """
            {
              "total_received": "3000000000000",
              "scanned_height": 310,
              "scanned_block_height": 310,
              "start_height": 0,
              "transaction_height": 320,
              "blockchain_height": 320,
              "transactions": [
                {
                  "id": 12345,
                  "hash": "9912",
                  "total_received": "3000000000000",
                  "total_sent": "0",
                  "fee": "0",
                  "unlock_time": 0,
                  "coinbase": false,
                  "mempool": true,
                  "mixin": 16,
                  "recipient": {"maj_i": 0, "min_i": 0}
                }
              ]
            }
        """.trimIndent()
        val tx = MoneroJson.deserialize(json, MoneroLwsAddressTxsResponse::class.java).transactions!![0]
        assertEquals(true, tx.isMempool)
        assertNull(tx.height)
        assertNull(tx.timestamp)
    }

    @Test
    fun addressTxsResponse_WithNothingSet_LeavesOutWhatTheServerLeavesOut() {
        val response = MoneroLwsAddressTxsResponse()
        response.totalReceived = BigInteger.ONE
        val json = write(response)
        assertFalse(json.contains("transactions"), json)
        assertFalse(json.contains("lookahead"), json)
        assertFalse(json.contains("spent_outputs"), json)
    }

    @Test
    fun spentOutputs_EmptyIsAValue_NotAFieldThatIsNotSet() {
        val response = MoneroJson.deserialize("""{"spent_outputs":[]}""", MoneroLwsAddressInfoResponse::class.java)
        assertEquals(emptyList(), response.spentOutputs)
        assertSameJson("""{"spent_outputs":[]}""", write(response))
        assertNull(MoneroJson.deserialize("{}", MoneroLwsAddressInfoResponse::class.java).spentOutputs)
    }

    // get_version

    @Test
    fun version_ReadsTheCapture() {
        val version = readFixture("version", MoneroLwsVersion::class.java)
        assertEquals("monero-lws", version.serverType)
        assertEquals("1.1-alpha", version.serverVersion)
        assertEquals("52cf3fe54b2ddf15351ed7b6d62880edc6c603e8", version.lastGitCommitHash)
        assertEquals("2026/10/16 23:43:21", version.lastGitCommitDate)
        assertEquals("master", version.gitBranchName)
        assertEquals("0.18.1.0-d56daee99", version.moneroVersionFull)
        assertEquals(3707000L, version.blockchainHeight)
        assertEquals(65543L, version.api)
        assertEquals(0L, version.maxSubaddresses)
        assertEquals("main", version.networkType)
        assertEquals(false, version.isTestnet)
    }

    // submit_raw_tx

    @Test
    fun submitRawTx_WritesAndReads() {
        val request = MoneroLwsSubmitRawTxRequest()
        request.tx = "deadbeef"
        assertSameJson("""{"tx":"deadbeef"}""", write(request))

        val response = MoneroJson.deserialize("""{"status":"OK"}""", MoneroLwsSubmitRawTxResponse::class.java)
        assertEquals("OK", response.status)
    }

    // the uint64 amounts, which the wire writes as decimal strings

    @Test
    fun amounts_ReadTheWholeRangeOfAnUnsigned64() {
        // 2^53 + 1, string parsing is precision safe
        assertEquals(BigInteger.valueOf(9007199254740993L), readAmount(""""9007199254740993""""))
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), readAmount(""""18446744073709551615""""))
        assertEquals(BigInteger.ZERO, readAmount(""""0""""))
    }

    @Test
    fun amounts_ThatAreNotADecimalString_AreRejected() {
        for (json in listOf("\"\"", "\" 42\"", "\"42 \"", "\"12.5\"", "\"1e9\"", "\"0x10\"", "\"-42\"", "\"+42\"", "42")) {
            val e = assertFailsWith<MoneroError>(json) { MoneroJson.deserialize("""{"amount":$json}""", MoneroLwsSpend::class.java) }
            assertTrue(e.message!!.contains("uint64"), e.message)
        }
    }

    @Test
    fun amounts_ThatDoNotFitAnUnsigned64_AreRejected() {
        assertFailsWith<MoneroError> { readAmount(""""18446744073709551616"""") }
    }

    @Test
    fun amounts_AreWrittenAsDecimalStrings() {
        val spend = MoneroLwsSpend()
        spend.amount = BigInteger.valueOf(175921021585277)
        spend.keyImage = "k"
        assertSameJson("""{"amount":"175921021585277","key_image":"k"}""", write(spend))
    }

    @Test
    fun amounts_ThatDoNotFitAnUnsigned64_AreNotWritten() {
        for (amount in listOf(BigInteger.valueOf(-5), BigInteger.ONE.shiftLeft(64))) {
            val spend = MoneroLwsSpend()
            spend.amount = amount
            assertFailsWith<MoneroError>("$amount") { write(spend) }
        }
    }

    private fun readAmount(json: String): BigInteger {
        return MoneroJson.deserialize("""{"amount":$json}""", MoneroLwsSpend::class.java).amount!!
    }
}
