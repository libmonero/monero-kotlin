package monero.common

import com.fasterxml.jackson.core.type.TypeReference
import java.math.BigInteger
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.ModelTestSupport
import monero.daemon.model.MoneroBlockHeader
import monero.daemon.model.MoneroDaemonInfo
import monero.daemon.model.MoneroKeyImageSpentStatus
import monero.daemon.model.MoneroNetworkType
import monero.daemon.model.MoneroTx

/**
 * Serialization and deserialization of the JSON that monero-cpp and monero-c write, which the models read and write
 * through MoneroJson. Every test runs without monero-c, a node or the network.
 */
class MoneroJsonTest {

    @Test
    fun serialize_LeavesOutFieldsThatAreNotSet_ButNotZeroAndFalse() {
        val tx = MoneroTx()
        tx.hash = "abc"
        tx.fee = BigInteger.ZERO
        tx.isMinerTx = false
        tx.numConfirmations = 0
        ModelTestSupport.assertSameJson("""{"hash":"abc","fee":0,"isMinerTx":false,"numConfirmations":0}""", MoneroJson.serialize(tx))
    }

    @Test
    fun serialize_WritesCompactJson() {
        val header = MoneroBlockHeader()
        header.height = 7
        header.hash = "h"
        val json = MoneroJson.serialize(header)
        ModelTestSupport.assertSameJson("""{"hash":"h","height":7}""", json)
        assertTrue(!json.contains(' ') && !json.contains('\n'))
    }

    @Test
    fun deserialize_ReadsGenericTypes() {
        val hashes = MoneroJson.deserialize<List<String>>("""["a","b"]""")
        assertEquals(listOf("a", "b"), hashes)
        val heights = MoneroJson.deserialize("[1,2,3]", object : TypeReference<List<Long>>() {})
        assertEquals(listOf(1L, 2L, 3L), heights)
    }

    @Test
    fun deserialize_OfNull_IsAnErrorThatNamesTheType() {
        val e = assertFailsWith<MoneroError> { MoneroJson.deserialize("null", MoneroDaemonInfo::class.java) }
        assertTrue(e.message!!.contains("MoneroDaemonInfo"), e.message)
    }

    @Test
    fun deserialize_OfJsonThatIsNotValid_IsAnErrorThatNamesTheType() {
        for (json in listOf("", "{", """{"height":"high"}""", """{"height":[1]}""", """[1,2]""", "nope")) {
            val e = assertFailsWith<MoneroError>(json) { MoneroJson.deserialize(json, MoneroDaemonInfo::class.java) }
            assertTrue(e.message!!.contains("MoneroDaemonInfo"), e.message)
        }
    }

    @Test
    fun numbers_UseTheWholeRangeOfTheirType() {
        val max = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
        val json = """{"reward":$max,"height":${Long.MAX_VALUE},"numTxs":${Int.MAX_VALUE},"nonce":4294967295,"difficultyLow":$max,"difficultyHigh":$max}"""
        val header = MoneroJson.deserialize(json, MoneroBlockHeader::class.java)
        assertEquals(max, header.reward)
        assertEquals(Long.MAX_VALUE, header.height)
        assertEquals(Int.MAX_VALUE, header.numTxs)
        assertEquals(4294967295L, header.nonce)
        assertEquals(max, header.difficultyLow)
        ModelTestSupport.assertSameJson(json, MoneroJson.serialize(header))
    }

    @Test
    fun text_WithEscapesAndUnicode_SurvivesTheRoundTrip() {
        val tx = MoneroTx()
        tx.hash = "quote \" backslash \\ newline \n tab \t unicode é中😀 control \u0001"
        val read = MoneroJson.deserialize(MoneroJson.serialize(tx), MoneroTx::class.java)
        assertEquals(tx.hash, read.hash)
    }

    @Test
    fun text_EmptyIsAValue_NotAFieldThatIsNotSet() {
        val tx = MoneroTx()
        tx.hash = ""
        assertEquals("""{"hash":""}""", MoneroJson.serialize(tx))
        assertEquals("", MoneroJson.deserialize("""{"hash":""}""", MoneroTx::class.java).hash)
        assertNull(MoneroJson.deserialize("{}", MoneroTx::class.java).hash)
    }

    @Test
    fun emptyLists_AreNotTheSameAsListsThatAreNotSet() {
        val tx = MoneroJson.deserialize("""{"inputs":[],"outputs":[]}""", MoneroTx::class.java)
        assertEquals(emptyList(), tx.inputs)
        assertEquals(emptyList(), tx.outputs)
        ModelTestSupport.assertSameJson("""{"inputs":[],"outputs":[]}""", MoneroJson.serialize(tx))
        assertNull(MoneroJson.deserialize("{}", MoneroTx::class.java).inputs)
    }

    @Test
    fun extra_IsAnArrayOfNumbers_NotBase64() {
        val tx = MoneroJson.deserialize("""{"extra":[1,129,255,0]}""", MoneroTx::class.java)
        assertEquals(listOf(1, 129, 255, 0), tx.extra)
        assertEquals("""{"extra":[1,129,255,0]}""", MoneroJson.serialize(tx))
    }

    @Test
    fun enumerations_HaveTheNumbersOfMoneroCpp() {
        assertEquals(listOf("MAINNET", "TESTNET", "STAGENET", "REGTEST"), MoneroNetworkType.entries.map { it.name })
        for ((number, type) in MoneroNetworkType.entries.withIndex()) {
            val info = MoneroJson.deserialize("""{"networkType":$number}""", MoneroDaemonInfo::class.java)
            assertEquals(type, info.networkType)
            assertEquals("""{"networkType":$number}""", MoneroJson.serialize(info))
        }
        assertEquals(
            listOf(MoneroKeyImageSpentStatus.NOT_SPENT, MoneroKeyImageSpentStatus.CONFIRMED, MoneroKeyImageSpentStatus.TX_POOL),
            MoneroJson.deserialize<List<MoneroKeyImageSpentStatus>>("[0,1,2]")
        )
        assertEquals("[0,1,2]", MoneroJson.serialize(MoneroKeyImageSpentStatus.entries))
    }

    @Test
    fun networkType_NumberThatIsNotANetwork_IsRejected() {
        for (number in listOf(-1, 4, 100)) {
            assertFailsWith<MoneroError>("$number") { MoneroJson.deserialize("""{"networkType":$number}""", MoneroDaemonInfo::class.java) }
        }
    }

    @Test
    fun networkType_ParsesTheNamesOfTheNetworks() {
        assertEquals(MoneroNetworkType.MAINNET, MoneroNetworkType.parse("mainnet"))
        assertEquals(MoneroNetworkType.TESTNET, MoneroNetworkType.parse(" Testnet "))
        assertEquals(MoneroNetworkType.STAGENET, MoneroNetworkType.parse("STAGENET"))
        assertEquals(MoneroNetworkType.REGTEST, MoneroNetworkType.parse("regtest"))
        for (name in listOf("", "main", "mainnet2", "0")) assertFailsWith<MoneroError>(name) { MoneroNetworkType.parse(name) }
    }

    @Test
    fun txDefaultPaymentId_IsEightZeroBytes() {
        assertEquals("0000000000000000", MoneroTx.DEFAULT_PAYMENT_ID)
        assertEquals(16, MoneroTx.DEFAULT_PAYMENT_ID.length)
    }

    @Test
    fun json_CanBeUsedFromManyThreads() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            val json = ModelTestSupport.fixture("daemon", "daemon_info")
            val tasks = (1..40).map {
                pool.submit<String> { MoneroJson.serialize(MoneroJson.deserialize(json, MoneroDaemonInfo::class.java)) }
            }
            for (task in tasks) ModelTestSupport.assertSameJson(json, task.get())
        } finally {
            pool.shutdown()
        }
    }
}
