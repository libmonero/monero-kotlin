package monero.common

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import java.io.File
import java.io.IOException
import java.math.BigInteger
import monero.ModelTestSupport
import monero.NativeTestSupport
import monero.daemon.model.MoneroBlock
import monero.daemon.model.MoneroNetworkType
import monero.internal.MoneroNative
import monero.internal.SizeT
import monero.wallet.model.MoneroIncomingTransfer
import monero.wallet.model.MoneroOutputWallet
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxWallet
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Monero utilities unit tests.
 */
class MoneroUtilsTest {

    @BeforeTest
    fun requireLibrary() {
        NativeTestSupport.assumeAvailable()
    }

    // the configuration of these tests, read from the fixture with the addresses, the keys and the message to serialize
    private val config = MoneroJson.deserialize(ModelTestSupport.fixture("utils", "test_monero_utils"), Config::class.java)

    // Can get integrated addresses
    @Test
    fun testGetIntegratedAddress() {
        val primaryAddress = config.stagenet.primaryAddress4
        val subaddress = config.stagenet.subaddress4
        val paymentId = "03284e41c342f036"
        val networkType = MoneroNetworkType.STAGENET

        // get integrated address with randomly generated payment id
        var integratedAddress = MoneroUtils.getIntegratedAddress(networkType, primaryAddress, "")
        assertEquals(primaryAddress, integratedAddress.standardAddress)
        assertEquals(16, integratedAddress.paymentId!!.length)
        assertEquals(106, integratedAddress.integratedAddress!!.length)

        // get integrated address with specific payment id
        integratedAddress = MoneroUtils.getIntegratedAddress(networkType, primaryAddress, paymentId)
        assertEquals(primaryAddress, integratedAddress.standardAddress)
        assertEquals(paymentId, integratedAddress.paymentId)
        assertEquals(106, integratedAddress.integratedAddress!!.length)

        // get integrated address with subaddress
        integratedAddress = MoneroUtils.getIntegratedAddress(networkType, subaddress, paymentId)
        assertEquals(subaddress, integratedAddress.standardAddress)
        assertEquals(paymentId, integratedAddress.paymentId)
        assertEquals(106, integratedAddress.integratedAddress!!.length)

        // get integrated address with invalid payment id
        val e = assertFailsWith<MoneroError> { MoneroUtils.getIntegratedAddress(networkType, primaryAddress, "123") }
        assertEquals("Invalid payment id", e.message)
    }

    // Can serialize heights with small numbers
    @Test
    fun testSerializeHeightsSmall() {
        val jsonMap = mapOf("heights" to listOf(111, 222, 333))
        val binary = MoneroUtils.dictToBinary(jsonMap)
        assertTrue(binary.isNotEmpty())

        assertEquals(jsonMap, MoneroUtils.binaryToDict(binary))
    }

    // Can serialize heights with big numbers
    @Test
    fun testSerializeHeightsBig() {
        val jsonMap = mapOf("heights" to listOf(123456, 1234567, 870987))
        val binary = MoneroUtils.dictToBinary(jsonMap)
        assertTrue(binary.isNotEmpty())

        assertEquals(jsonMap, MoneroUtils.binaryToDict(binary))
    }

    // can serialize height with large unsigned values
    @Test
    fun testSerializeLargeUnsignedValues() {
        val bigValue = BigInteger("18446744073709551615") // UINT64_MAX
        val jsonMap = mapOf("heights" to listOf(bigValue))
        val binary = MoneroUtils.dictToBinary(jsonMap)
        assertTrue(binary.isNotEmpty())

        val jsonMap2 = MoneroUtils.binaryToDict(binary)
        assertEquals(jsonMap, jsonMap2)
        val heights = jsonMap2["heights"] as List<*>
        assertTrue(BigInteger(heights[0].toString()) > BigInteger.ZERO, "uint64 > INT64_MAX must not serialize as negative")
    }

    // Can serialize jsonMap with text
    @Test
    fun testSerializeTextShort() {
        val msg = config.serialization.msg
        assertTrue(msg.isNotEmpty())
        val jsonMap = mapOf("msg" to msg)
        val binary = MoneroUtils.dictToBinary(jsonMap)
        assertTrue(binary.isNotEmpty())

        assertEquals(jsonMap, MoneroUtils.binaryToDict(binary))
    }

    // Can serialize json with long text
    @Test
    fun testSerializeTextLong() {
        val msg = config.serialization.msg
        val jsonMap = mapOf("msg" to (1..14).joinToString("") { "$msg\n" })
        val binary = MoneroUtils.dictToBinary(jsonMap)
        assertTrue(binary.isNotEmpty())

        assertEquals(jsonMap, MoneroUtils.binaryToDict(binary))
    }

    // Can serialize text that is not ASCII, which crosses the bridge as UTF-8: a binding that falls back to the
    // charset of the platform mangles it silently
    @Test
    fun testSerializeNonAsciiText() {
        for (text in listOf("héllo wörld", "日本語のシード", "emoji 😀 and symbols ✓✗")) {
            val jsonMap = mapOf("msg" to text)
            val binary = MoneroUtils.dictToBinary(jsonMap)
            assertTrue(binary.isNotEmpty())

            assertEquals(jsonMap, MoneroUtils.binaryToDict(binary))
        }
    }

    // The binary form is the wire format of the binary RPC of a daemon, which a round trip with itself cannot pin:
    // a codec that is wrong the same way twice passes it. The bytes that monero-c writes for a fixed map, which
    // are the ones that python's dict_to_binary() produces for the same one, are checked in as a fixture, and must
    // come out of dictToBinary() exactly
    @Test
    fun testBinaryFormMatchesTheGoldenVector() {
        val jsonMap = mapOf("heights" to listOf(111, 222, 333))
        val url = MoneroUtilsTest::class.java.getResource("/fixtures/utils/heights.bin") ?: fail("No fixture utils/heights.bin")
        val golden = File(url.toURI()).readBytes()

        assertContentEquals(golden, MoneroUtils.dictToBinary(jsonMap))

        // and the golden vector decodes back to the map it was made from
        assertEquals(jsonMap, MoneroUtils.binaryToDict(golden))
    }

    // Can validate addresses
    @Test
    fun testAddressValidation() {
        val mainnet = config.mainnet
        val testnet = config.testnet
        val stagenet = config.stagenet

        // test mainnet primary address validation
        assertTrue(MoneroUtils.isValidAddress(mainnet.primaryAddress1, MoneroNetworkType.MAINNET))
        assertTrue(MoneroUtils.isValidAddress(mainnet.primaryAddress2, MoneroNetworkType.MAINNET))
        assertTrue(MoneroUtils.isValidAddress(mainnet.primaryAddress3, MoneroNetworkType.MAINNET))

        // test mainnet integrated address validation
        MoneroUtils.validateAddress(mainnet.integrated1, MoneroNetworkType.MAINNET)
        MoneroUtils.validateAddress(mainnet.integrated2, MoneroNetworkType.MAINNET)
        MoneroUtils.validateAddress(mainnet.integrated3, MoneroNetworkType.MAINNET)

        // test mainnet subaddress validation
        MoneroUtils.validateAddress(mainnet.subaddress1, MoneroNetworkType.MAINNET)
        MoneroUtils.validateAddress(mainnet.subaddress2, MoneroNetworkType.MAINNET)
        MoneroUtils.validateAddress(mainnet.subaddress3, MoneroNetworkType.MAINNET)

        // test testnet primary address validation
        assertTrue(MoneroUtils.isValidAddress(testnet.primaryAddress1, MoneroNetworkType.TESTNET))
        assertTrue(MoneroUtils.isValidAddress(testnet.primaryAddress2, MoneroNetworkType.TESTNET))
        assertTrue(MoneroUtils.isValidAddress(testnet.primaryAddress3, MoneroNetworkType.TESTNET))

        // test testnet integrated address validation
        assertTrue(MoneroUtils.isValidAddress(testnet.integrated1, MoneroNetworkType.TESTNET))
        assertTrue(MoneroUtils.isValidAddress(testnet.integrated2, MoneroNetworkType.TESTNET))
        assertTrue(MoneroUtils.isValidAddress(testnet.integrated3, MoneroNetworkType.TESTNET))

        // test testnet subaddress validation
        assertTrue(MoneroUtils.isValidAddress(testnet.subaddress1, MoneroNetworkType.TESTNET))
        assertTrue(MoneroUtils.isValidAddress(testnet.subaddress2, MoneroNetworkType.TESTNET))
        assertTrue(MoneroUtils.isValidAddress(testnet.subaddress3, MoneroNetworkType.TESTNET))

        // test stagenet primary address validation
        assertTrue(MoneroUtils.isValidAddress(stagenet.primaryAddress1, MoneroNetworkType.STAGENET))
        assertTrue(MoneroUtils.isValidAddress(stagenet.primaryAddress2, MoneroNetworkType.STAGENET))
        assertTrue(MoneroUtils.isValidAddress(stagenet.primaryAddress3, MoneroNetworkType.STAGENET))

        // test stagenet integrated address validation
        assertTrue(MoneroUtils.isValidAddress(stagenet.integrated1, MoneroNetworkType.STAGENET))
        assertTrue(MoneroUtils.isValidAddress(stagenet.integrated2, MoneroNetworkType.STAGENET))
        assertTrue(MoneroUtils.isValidAddress(stagenet.integrated3, MoneroNetworkType.STAGENET))

        // test stagenet subaddress validation
        assertTrue(MoneroUtils.isValidAddress(stagenet.subaddress1, MoneroNetworkType.STAGENET))
        assertTrue(MoneroUtils.isValidAddress(stagenet.subaddress2, MoneroNetworkType.STAGENET))
        assertTrue(MoneroUtils.isValidAddress(stagenet.subaddress3, MoneroNetworkType.STAGENET))

        // test invalid addresses on mainnet
        testInvalidAddress(null, MoneroNetworkType.MAINNET)
        testInvalidAddress("", MoneroNetworkType.MAINNET)
        testInvalidAddress(mainnet.invalid1, MoneroNetworkType.MAINNET)
        testInvalidAddress(mainnet.invalid2, MoneroNetworkType.MAINNET)
        testInvalidAddress(mainnet.invalid3, MoneroNetworkType.MAINNET)

        // test invalid addresses on testnet
        testInvalidAddress(null, MoneroNetworkType.TESTNET)
        testInvalidAddress("", MoneroNetworkType.TESTNET)
        testInvalidAddress(testnet.invalid1, MoneroNetworkType.TESTNET)
        testInvalidAddress(testnet.invalid2, MoneroNetworkType.TESTNET)
        testInvalidAddress(testnet.invalid3, MoneroNetworkType.TESTNET)

        // test invalid addresses on stagenet
        testInvalidAddress(null, MoneroNetworkType.STAGENET)
        testInvalidAddress("", MoneroNetworkType.STAGENET)
        testInvalidAddress(stagenet.invalid1, MoneroNetworkType.STAGENET)
        testInvalidAddress(stagenet.invalid2, MoneroNetworkType.STAGENET)
        testInvalidAddress(stagenet.invalid3, MoneroNetworkType.STAGENET)
    }

    // Can validate keys
    @Test
    fun testKeyValidation() {
        val keys = config.keys
        val invalidHex64 = "z".repeat(64) // right length but not hex

        // test private view key validation
        assertTrue(MoneroUtils.isValidPrivateViewKey(keys.privateViewKey))
        MoneroUtils.validatePrivateViewKey(keys.privateViewKey)
        testInvalidPrivateViewKey("")
        testInvalidPrivateViewKey(null)
        testInvalidPrivateViewKey(keys.invalidPrivateViewKey)
        testInvalidPrivateViewKey(invalidHex64)

        // test public view key validation
        assertTrue(MoneroUtils.isValidPublicViewKey(keys.publicViewKey))
        MoneroUtils.validatePublicViewKey(keys.publicViewKey)
        testInvalidPublicViewKey("")
        testInvalidPublicViewKey(null)
        testInvalidPublicViewKey(keys.invalidPublicViewKey)
        testInvalidPublicViewKey(invalidHex64)

        // test private spend key validation
        assertTrue(MoneroUtils.isValidPrivateSpendKey(keys.privateSpendKey))
        testInvalidPrivateSpendKey("")
        testInvalidPrivateSpendKey(null)
        testInvalidPrivateSpendKey(keys.invalidPrivateSpendKey)
        testInvalidPrivateSpendKey(invalidHex64)

        // test public spend key validation
        assertTrue(MoneroUtils.isValidPublicSpendKey(keys.publicSpendKey))
        MoneroUtils.validatePublicSpendKey(keys.publicSpendKey)
        testInvalidPublicSpendKey("")
        testInvalidPublicSpendKey(null)
        testInvalidPublicSpendKey(keys.invalidPublicSpendKey)
        testInvalidPublicSpendKey(invalidHex64)
    }

    // Can validate seed
    @Test
    fun testMnemonicValidation() {
        val seed = config.keys.seed

        // test valid seed
        MoneroUtils.validateMnemonic(seed)
        assertTrue(MoneroUtils.isValidMnemonic(seed), "Invalid seed: $seed")
        assertTrue(MoneroUtils.isValidMnemonic(seed, "English"), "Expected valid English seed: $seed")

        // test invalid seed language
        assertFalse(MoneroUtils.isValidMnemonic(seed, "Spanish"), "Expected invalid Spanish seed: $seed")

        // test invalid seed
        assertFalse(MoneroUtils.isValidMnemonic("invalid monero wallet seed"))

        // test empty seed
        assertFalse(MoneroUtils.isValidMnemonic(""))
    }

    // Can validate language
    @Test
    fun testSeedLanguageValidation() {
        val languages = listOf("Italian", "English", "German")

        for (language in languages) {
            assertTrue(MoneroUtils.isValidLanguage(language), "Expected valid language: $language")
        }

        val invalidLanguages = listOf("", "english", "italian")

        for (language in invalidLanguages) {
            assertFalse(MoneroUtils.isValidLanguage(language), "Expected invalid language: $language")
        }
    }

    // Can validate payment id
    @Test
    fun testPaymentIdValidation() {
        val paymentIds = listOf(
            "43e04076e176b768", "ef35647e9842991c",
            "8434d5452ad1b0ab", "3b5ac230d2666177",
            "87fdf837b5e6a390", "304e0fa65b9c9e14"
        )

        for (paymentId in paymentIds) {
            assertTrue(MoneroUtils.isValidPaymentId(paymentId), "Expected valid payment id: $paymentId")
            MoneroUtils.validatePaymentId(paymentId)
        }

        // validate long payment id
        assertTrue(MoneroUtils.isValidPaymentId("87fdf837b5e6a390ef35647e9842991c8434d5452ad1b0ab304e0fa65b9c9e14"))

        val invalidPaymentIds = listOf(
            "", "wijqwnn38y",
            "87fdf837b5e6a39", "3b5ac230d26661778",
            "304e0fa65b9c9e14304e0fa65b9c9e14",
            "z".repeat(16), "z".repeat(64)
        )

        for (paymentId in invalidPaymentIds) {
            assertFalse(MoneroUtils.isValidPaymentId(paymentId), "Expected invalid payment id: $paymentId")
            val e = assertFailsWith<MoneroError>(paymentId) { MoneroUtils.validatePaymentId(paymentId) }
            assertEquals("payment id expected to be 64 or 16 hex characters", e.message)
        }
    }

    // Can validate a long payment id specifically
    @Test
    fun testPaymentIdLongValidation() {
        val longPaymentId = "87fdf837b5e6a390ef35647e9842991c8434d5452ad1b0ab304e0fa65b9c9e14"
        assertTrue(MoneroUtils.isValidPaymentIdLong(longPaymentId))

        val shortPaymentId = "87fdf837b5e6a390"
        assertFalse(MoneroUtils.isValidPaymentIdLong(shortPaymentId))

        val invalidPaymentIds = listOf("", "wijqwnn38y", longPaymentId.dropLast(1))

        for (paymentId in invalidPaymentIds) {
            assertFalse(MoneroUtils.isValidPaymentIdLong(paymentId), "Expected invalid long payment id: $paymentId")
        }
    }

    // Can validate a short payment id specifically
    @Test
    fun testPaymentIdShortValidation() {
        val shortPaymentId = "87fdf837b5e6a390"
        assertTrue(MoneroUtils.isValidPaymentIdShort(shortPaymentId))

        val longPaymentId = "87fdf837b5e6a390ef35647e9842991c8434d5452ad1b0ab304e0fa65b9c9e14"
        assertFalse(MoneroUtils.isValidPaymentIdShort(longPaymentId))

        val invalidPaymentIds = listOf("", "wijqwnn38y", shortPaymentId.dropLast(1))

        for (paymentId in invalidPaymentIds) {
            assertFalse(MoneroUtils.isValidPaymentIdShort(paymentId), "Expected invalid short payment id: $paymentId")
        }
    }

    // Can validate a long payment id, raising on failure
    @Test
    fun testValidatePaymentIdLong() {
        val longPaymentId = "87fdf837b5e6a390ef35647e9842991c8434d5452ad1b0ab304e0fa65b9c9e14"
        MoneroUtils.validatePaymentIdLong(longPaymentId)

        var e = assertFailsWith<MoneroError> { MoneroUtils.validatePaymentIdLong("87fdf837b5e6a390") } // too short
        assertTrue(e.message!!.contains("Invalid long payment id"), e.message)

        e = assertFailsWith<MoneroError> { MoneroUtils.validatePaymentIdLong("wijqwnn38y") } // not hex
        assertTrue(e.message!!.contains("Invalid long payment id"), e.message)
    }

    // Can validate a short payment id, raising on failure
    @Test
    fun testValidatePaymentIdShort() {
        val shortPaymentId = "87fdf837b5e6a390"
        MoneroUtils.validatePaymentIdShort(shortPaymentId)

        var e = assertFailsWith<MoneroError> { MoneroUtils.validatePaymentIdShort("87fdf837b5e6a390ef35647e9842991c8434d5452ad1b0ab304e0fa65b9c9e14") } // too long
        assertTrue(e.message!!.contains("Invalid short payment id"), e.message)

        e = assertFailsWith<MoneroError> { MoneroUtils.validatePaymentIdShort("wijqwnn38y") } // not hex
        assertTrue(e.message!!.contains("Invalid short payment id"), e.message)
    }

    // Can convert between XMR and atomic units
    @Test
    fun testAtomicUnitConversion() {
        assertEquals(BigInteger.valueOf(1000000000000L), MoneroUtils.xmrToAtomicUnits(1.0))
        assertEquals(1.0, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(1000000000000L)))
        assertEquals(BigInteger.valueOf(1000000000L), MoneroUtils.xmrToAtomicUnits(0.001))
        assertEquals(0.001, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(1000000000L)))
        assertEquals(BigInteger.valueOf(250000000000L), MoneroUtils.xmrToAtomicUnits(0.25))
        assertEquals(0.25, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(250000000000L)))
        assertEquals(BigInteger.valueOf(1250000000000L), MoneroUtils.xmrToAtomicUnits(1.25))
        assertEquals(1.25, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(1250000000000L)))
        assertEquals(BigInteger.valueOf(2796726190000L), MoneroUtils.xmrToAtomicUnits(2.79672619))
        assertEquals(2.79672619, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(2796726190000L)))
        assertEquals(BigInteger.valueOf(2796726190001L), MoneroUtils.xmrToAtomicUnits(2.796726190001))
        assertEquals(2.796726190001, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(2796726190001L)))
        assertEquals(BigInteger.valueOf(2796726189999L), MoneroUtils.xmrToAtomicUnits(2.796726189999))
        assertEquals(2.796726189999, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(2796726189999L)))
        assertEquals(BigInteger.valueOf(2796726180000L), MoneroUtils.xmrToAtomicUnits(2.79672618))
        assertEquals(2.79672618, MoneroUtils.atomicUnitsToXmr(BigInteger.valueOf(2796726180000L)))
    }

    // xmrToAtomicUnits(0.0) and negative zero are valid, not errors
    @Test
    fun testXmrToAtomicUnitsZero() {
        assertEquals(BigInteger.ZERO, MoneroUtils.xmrToAtomicUnits(0.0))
        // -0.0 < 0 is False in IEEE 754, so it must not be rejected by the "amount must be non-negative" check
        assertEquals(BigInteger.ZERO, MoneroUtils.xmrToAtomicUnits(-0.0))
    }

    // Amounts smaller than half an atomic unit (1e-12 XMR) round down to 0
    @Test
    fun testXmrToAtomicUnitsRoundsDownToZero() {
        assertEquals(BigInteger.ZERO, MoneroUtils.xmrToAtomicUnits(1e-13))
        assertEquals(BigInteger.ZERO, MoneroUtils.xmrToAtomicUnits(4e-13))
    }

    // xmrToAtomicUnits() rounds the underlying long double * 1e12 product to the nearest atomic unit (away from zero
    // on an exact .5). Decimal literals ending in .5e-12 are not necessarily exact halves once represented as a double,
    // so which way they round depends on whether the closest double is a hair above or below the nominal decimal value,
    // and that itself can depend on the platform's "long double" precision (80-bit extended on x86_64 Linux/glibc, but
    // identical to a plain 64-bit double on Apple Silicon/macOS), so the same literal can round differently on
    // different platforms. This is inherent to IEEE 754, not an inconsistency in xmrToAtomicUnits() itself, assert the
    // result lands on one of the two atomic units the value sits between, not a specific platform's tie-break.
    @Test
    fun testXmrToAtomicUnitsHalfAtomicUnitRounding() {
        val cases = listOf(
            Triple(0.5e-12, 0L, 1L),
            Triple(1.5e-12, 1L, 2L),
            Triple(2.5e-12, 2L, 3L),
            Triple(3.5e-12, 3L, 4L)
        )
        for ((amountXmr, floorAtomic, ceilAtomic) in cases) {
            val actual = MoneroUtils.xmrToAtomicUnits(amountXmr)
            assertTrue(
                actual == BigInteger.valueOf(floorAtomic) || actual == BigInteger.valueOf(ceilAtomic),
                "xmrToAtomicUnits($amountXmr) == $actual, expected $floorAtomic or $ceilAtomic"
            )
        }
    }

    // amountXmr must be finite and non-negative
    @Test
    fun testXmrToAtomicUnitsInvalidAmount() {
        for (amountXmr in listOf(-1.0, -0.0000000001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val e = assertFailsWith<MoneroError>("$amountXmr") { MoneroUtils.xmrToAtomicUnits(amountXmr) }
            assertTrue(e.message!!.contains("amount must be a finite, non-negative number"), e.message)
        }
    }

    // amounts whose rounded atomic-unit value would overflow uint64_t are rejected rather than silently wrapping or
    // invoking undefined behavior on the cast
    @Test
    fun testXmrToAtomicUnitsOverflow() {
        // UINT64_MAX atomic units is ~18446744.0737... XMR; comfortably over that (with margin for float imprecision)
        // must raise
        var e = assertFailsWith<MoneroError> { MoneroUtils.xmrToAtomicUnits(18446745.0) }
        assertTrue(e.message!!.contains("amount exceeds maximum representable atomic units"), e.message)
        e = assertFailsWith<MoneroError> { MoneroUtils.xmrToAtomicUnits(2e22) }
        assertTrue(e.message!!.contains("amount exceeds maximum representable atomic units"), e.message)
    }

    // values comfortably below the uint64_t boundary succeed
    @Test
    fun testXmrToAtomicUnitsNearUint64MaxBoundary() {
        val uint64Max = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
        val boundaryXmr = uint64Max.toDouble() / 1e12 // ~18446744.073709551615 XMR
        val marginXmr = 1.0

        val safelyBelow = boundaryXmr - marginXmr
        val below = MoneroUtils.xmrToAtomicUnits(safelyBelow)
        // two-sided: the atomic units are an unsigned 64-bit number, which a signed reading of the same 64 bits is
        // not, since the near-boundary amounts are bigger than Long.MAX_VALUE
        assertTrue(below >= BigInteger.ZERO && below <= uint64Max, "xmrToAtomicUnits($safelyBelow) == $below, expected an unsigned 64-bit number")

        // an exact value above 2^63 pins the unsigned interpretation: the 64 bits of 18446744000000000000 read as
        // the negative -73709551616 when they are read as a signed long. 18446744.0 and 1e12 are exact as doubles,
        // and their product needs only 50 bits of mantissa, so every platform computes the same value
        assertEquals(BigInteger("18446744000000000000"), MoneroUtils.xmrToAtomicUnits(18446744.0))

        val safelyAbove = boundaryXmr + marginXmr
        val e = assertFailsWith<MoneroError> { MoneroUtils.xmrToAtomicUnits(safelyAbove) }
        assertTrue(e.message!!.contains("amount exceeds maximum representable atomic units"), e.message)
    }

    // Can get payment uri
    @Test
    fun testGetPaymentUri() {
        val address = config.mainnet.primaryAddress1
        val txConfig = buildPaymentUriConfig(address)
        val paymentUri = MoneroUtils.getPaymentUri(txConfig)
        val query = "tx_amount=0.250000000000&recipient_name=John%20Doe&tx_description=My%20transfer%20to%20wallet"
        assertEquals("monero:$address?$query", paymentUri)
    }

    // Test invalid payment uri address network type
    @Test
    fun testPaymentUriInvalidNetworkType() {
        val address = config.testnet.primaryAddress1
        val txConfig = buildPaymentUriConfig(address)
        val e = assertFailsWith<MoneroError> { MoneroUtils.getPaymentUri(txConfig) }

        // getPaymentUri() wraps make_uri()'s error with context, unlike e.g. validateAddress()
        assertEquals("Cannot make URI from supplied parameters: wrong address: $address", e.message)
    }

    // Test deprecated standalone payment id
    @Test
    fun testPaymentUriDeprecatedPaymentUri() {
        val address = config.testnet.primaryAddress1
        val txConfig = buildPaymentUriConfig(address)
        txConfig.paymentId = "03284e41c342f03603284e41c342f03603284e41c342f03603284e41c342f036"
        val e = assertFailsWith<MoneroError> { MoneroUtils.getPaymentUri(txConfig, MoneroNetworkType.TESTNET) }

        // getPaymentUri() wraps make_uri()'s error with context, unlike e.g. validateAddress()
        assertEquals("Cannot make URI from supplied parameters: Standalone payment id deprecated, use integrated address instead", e.message)
    }

    // Test single payment id given with an integrated address
    @Test
    fun testPaymentUriSinglePaymentIdWithIntegratedAddress() {
        val address = config.mainnet.integrated1
        val txConfig = buildPaymentUriConfig(address)
        txConfig.paymentId = "03284e41c342f03603284e41c342f03603284e41c342f03603284e41c342f036"
        val e = assertFailsWith<MoneroError> { MoneroUtils.getPaymentUri(txConfig) }

        // getPaymentUri() wraps make_uri()'s error with context, unlike e.g. validateAddress()
        assertEquals("Cannot make URI from supplied parameters: A single payment id is allowed", e.message)
    }

    // Can parse a payment uri without any query string
    @Test
    fun testParsePaymentUriNoQuery() {
        val address = config.mainnet.primaryAddress1
        val parsed = MoneroUtils.parsePaymentUri("monero:$address")
        assertEquals(address, parsed.destinations!![0].address)
        assertEquals(BigInteger.ZERO, parsed.destinations!![0].amount)
        assertNull(parsed.recipientName)
        assertNull(parsed.note)
        assertNull(parsed.paymentId)
    }

    // Can parse a payment uri with an empty query string
    @Test
    fun testParsePaymentUriEmptyQuery() {
        val address = config.mainnet.primaryAddress1
        val parsed = MoneroUtils.parsePaymentUri("monero:$address?")
        assertEquals(address, parsed.destinations!![0].address)
    }

    // Can parse a payment uri round trip built by getPaymentUri()
    @Test
    fun testParsePaymentUriRoundTrip() {
        val address = config.mainnet.primaryAddress1
        val txConfig = buildPaymentUriConfig(address)
        val uri = MoneroUtils.getPaymentUri(txConfig)
        val parsed = MoneroUtils.parsePaymentUri(uri)
        assertEquals(address, parsed.destinations!![0].address)
        assertEquals(txConfig.amount, parsed.destinations!![0].amount)
        assertEquals(txConfig.recipientName, parsed.recipientName)
        assertEquals(txConfig.note, parsed.note)
    }

    // Payment uri must use the "monero:" scheme
    @Test
    fun testParsePaymentUriWrongScheme() {
        val address = config.mainnet.primaryAddress1
        val e = assertFailsWith<MoneroError> { MoneroUtils.parsePaymentUri("bitcoin:$address") }
        assertEquals("""Error parsing URI: URI has wrong scheme (expected "monero:"): bitcoin:$address""", e.message)
    }

    // Payment uri address must be valid
    @Test
    fun testParsePaymentUriWrongAddress() {
        val address = config.mainnet.invalid1
        val e = assertFailsWith<MoneroError> { MoneroUtils.parsePaymentUri("monero:$address") }
        assertEquals("Error parsing URI: URI has wrong address: $address", e.message)
    }

    // Payment uri parameters must be "key=value" pairs
    @Test
    fun testParsePaymentUriMalformedParameter() {
        val address = config.mainnet.primaryAddress1
        val e = assertFailsWith<MoneroError> { MoneroUtils.parsePaymentUri("monero:$address?tx_amount") }
        assertEquals("Error parsing URI: URI has wrong parameter: tx_amount", e.message)
    }

    // Payment uri parameters must not repeat
    @Test
    fun testParsePaymentUriDuplicateParameter() {
        val address = config.mainnet.primaryAddress1
        val e = assertFailsWith<MoneroError> { MoneroUtils.parsePaymentUri("monero:$address?tx_amount=1&tx_amount=2") }
        assertEquals("Error parsing URI: URI has more than one instance of tx_amount", e.message)
    }

    // Payment uri tx_amount must be a valid amount
    @Test
    fun testParsePaymentUriInvalidAmount() {
        val address = config.mainnet.primaryAddress1
        val e = assertFailsWith<MoneroError> { MoneroUtils.parsePaymentUri("monero:$address?tx_amount=abc") }
        assertEquals("Error parsing URI: URI has invalid amount: abc", e.message)
    }

    // Payment uri tx_payment_id must be a valid long payment id
    @Test
    fun testParsePaymentUriInvalidPaymentId() {
        val address = config.mainnet.primaryAddress1
        val e = assertFailsWith<MoneroError> { MoneroUtils.parsePaymentUri("monero:$address?tx_payment_id=nothex") }
        assertEquals("Error parsing URI: Invalid payment id: nothex", e.message)
    }

    // Payment uri can carry a separate, valid tx_payment_id for a non-integrated address
    @Test
    fun testParsePaymentUriValidPaymentId() {
        val address = config.mainnet.primaryAddress1
        val paymentId = "a".repeat(64)
        val parsed = MoneroUtils.parsePaymentUri("monero:$address?tx_payment_id=$paymentId")
        assertEquals(paymentId, parsed.paymentId)
    }

    // Payment uri must not combine a separate tx_payment_id with an integrated address
    @Test
    fun testParsePaymentUriSeparatePaymentIdWithIntegratedAddress() {
        val address = config.mainnet.integrated1
        val e = assertFailsWith<MoneroError> { MoneroUtils.parsePaymentUri("monero:$address?tx_payment_id=${"a".repeat(64)}") }
        assertEquals("Error parsing URI: Separate payment id given with an integrated address", e.message)
    }

    // Unknown payment uri parameters are silently discarded
    @Test
    fun testParsePaymentUriUnknownParameter() {
        val address = config.mainnet.primaryAddress1
        val parsed = MoneroUtils.parsePaymentUri("monero:$address?foo=bar")
        assertEquals(address, parsed.destinations!![0].address)
    }

    // Can get version
    @Test
    fun testGetVersion() {
        val version = MoneroUtils.getVersion()
        assertTrue(version.isNotEmpty(), "Version is empty")
    }

    // Can get ring size
    @Test
    fun testGetRingSize() {
        assertEquals(16, MoneroUtils.getRingSize())
    }

    // Gather blocks

    @Test
    fun testGetBlocksFromTxsDedupAndOrder() {
        val block1 = MoneroBlock()
        block1.height = 100
        val block2 = MoneroBlock()
        block2.height = 200

        val tx1 = MoneroTxWallet()
        tx1.hash = "a".repeat(64)
        tx1.block = block1

        val tx2 = MoneroTxWallet()
        tx2.hash = "b".repeat(64)
        tx2.block = block2

        val tx3 = MoneroTxWallet() // shares block1 with tx1
        tx3.hash = "c".repeat(64)
        tx3.block = block1

        val blocks = MoneroUtils.getBlocksFromTxs(listOf(tx1, tx2, tx3))
        assertEquals(2, blocks.size) // block1 deduplicated despite appearing twice
        assertTrue(blocks[0] === block1) // blocks are returned in first-seen order
        assertTrue(blocks[1] === block2)
    }

    @Test
    fun testGetBlocksFromTxsUnconfirmedPlaceholder() {
        val tx1 = MoneroTxWallet()
        tx1.hash = "a".repeat(64)
        val tx2 = MoneroTxWallet()
        tx2.hash = "b".repeat(64)
        assertNull(tx1.block)
        assertNull(tx2.block)

        val blocks = MoneroUtils.getBlocksFromTxs(listOf(tx1, tx2))

        // unconfirmed (blockless) txs are grouped under one shared placeholder block
        assertEquals(1, blocks.size)
        val placeholder = blocks[0]
        assertNull(placeholder.height)
        assertEquals(2, placeholder.txs!!.size)

        // side effect: getBlocksFromTxs() mutates its inputs, attaching each unconfirmed tx to the placeholder block
        // it creates
        assertTrue(tx1.block === placeholder)
        assertTrue(tx2.block === placeholder)
    }

    @Test
    fun testGetBlocksFromTxsMixedConfirmedAndUnconfirmed() {
        val block = MoneroBlock()
        block.height = 100
        val confirmed = MoneroTxWallet()
        confirmed.hash = "a".repeat(64)
        confirmed.block = block

        val unconfirmed1 = MoneroTxWallet()
        unconfirmed1.hash = "b".repeat(64)
        val unconfirmed2 = MoneroTxWallet()
        unconfirmed2.hash = "c".repeat(64)

        val blocks = MoneroUtils.getBlocksFromTxs(listOf(confirmed, unconfirmed1, unconfirmed2))
        assertEquals(2, blocks.size) // the real block, plus one shared unconfirmed placeholder
        assertTrue(blocks[0] === block)
        assertNull(blocks[1].height)
        assertTrue(unconfirmed1.block === unconfirmed2.block)
        assertTrue(unconfirmed2.block === blocks[1])
    }

    @Test
    fun testGetBlocksFromTxsEmpty() {
        assertEquals(emptyList(), MoneroUtils.getBlocksFromTxs(emptyList()))
    }

    @Test
    fun testGetBlocksFromTransfersDedupAndOrder() {
        val block1 = MoneroBlock()
        block1.height = 100
        val block2 = MoneroBlock()
        block2.height = 200

        val tx1 = MoneroTxWallet()
        tx1.hash = "a".repeat(64)
        tx1.block = block1
        val t1 = MoneroIncomingTransfer()
        t1.tx = tx1

        val tx2 = MoneroTxWallet()
        tx2.hash = "b".repeat(64)
        tx2.block = block2
        val t2 = MoneroIncomingTransfer()
        t2.tx = tx2

        val tx3 = MoneroTxWallet() // tx shares block1 with t1
        tx3.hash = "c".repeat(64)
        tx3.block = block1
        val t3 = MoneroIncomingTransfer()
        t3.tx = tx3

        val blocks = MoneroUtils.getBlocksFromTransfers(listOf(t1, t2, t3))
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] === block1)
        assertTrue(blocks[1] === block2)
    }

    @Test
    fun testGetBlocksFromTransfersUnconfirmedPlaceholder() {
        val tx1 = MoneroTxWallet()
        tx1.hash = "a".repeat(64)
        val t1 = MoneroIncomingTransfer()
        t1.tx = tx1
        val tx2 = MoneroTxWallet()
        tx2.hash = "b".repeat(64)
        val t2 = MoneroIncomingTransfer()
        t2.tx = tx2
        assertNull(t1.tx!!.block)
        assertNull(t2.tx!!.block)

        val blocks = MoneroUtils.getBlocksFromTransfers(listOf(t1, t2))
        assertEquals(1, blocks.size)
        val placeholder = blocks[0]
        assertNull(placeholder.height)

        // side effect: mutates transfer.tx.block, same as getBlocksFromTxs()
        assertTrue(t1.tx!!.block === placeholder)
        assertTrue(t2.tx!!.block === placeholder)
    }

    @Test
    fun testGetBlocksFromTransfersMixedConfirmedAndUnconfirmed() {
        val block = MoneroBlock()
        block.height = 100
        val confirmedTx = MoneroTxWallet()
        confirmedTx.hash = "a".repeat(64)
        confirmedTx.block = block
        val confirmed = MoneroIncomingTransfer()
        confirmed.tx = confirmedTx

        val unconfirmedTx1 = MoneroTxWallet()
        unconfirmedTx1.hash = "b".repeat(64)
        val unconfirmed1 = MoneroIncomingTransfer()
        unconfirmed1.tx = unconfirmedTx1
        val unconfirmedTx2 = MoneroTxWallet()
        unconfirmedTx2.hash = "c".repeat(64)
        val unconfirmed2 = MoneroIncomingTransfer()
        unconfirmed2.tx = unconfirmedTx2

        val blocks = MoneroUtils.getBlocksFromTransfers(listOf(confirmed, unconfirmed1, unconfirmed2))
        assertEquals(2, blocks.size) // the real block, plus one shared unconfirmed placeholder
        assertTrue(blocks[0] === block)
        assertNull(blocks[1].height)
        assertTrue(unconfirmed1.tx!!.block === unconfirmed2.tx!!.block)
        assertTrue(unconfirmed2.tx!!.block === blocks[1])
    }

    @Test
    fun testGetBlocksFromTransfersMissingTxRaises() {
        // a transfer with no tx set is a legitimate, reachable state (it's just never assigned);
        // getBlocksFromTransfers() must raise a catchable exception, not dereference transfer.tx
        val transfer = MoneroIncomingTransfer()
        transfer.amount = BigInteger.valueOf(500000)
        val e = assertFailsWith<MoneroError> { MoneroUtils.getBlocksFromTransfers(listOf(transfer)) }
        assertEquals("Transfer has no tx", e.message)
    }

    @Test
    fun testGetBlocksFromTransfersMissingTxRaisesAfterPartialMutation() {
        // getBlocksFromTransfers() is not transactional: a transfer earlier in the list is already mutated (attached
        // to a placeholder block) before a later, invalid transfer aborts the whole call
        val tx = MoneroTxWallet()
        tx.hash = "a".repeat(64)
        val good = MoneroIncomingTransfer()
        good.tx = tx
        assertNull(good.tx!!.block)

        val bad = MoneroIncomingTransfer() // no tx
        assertFailsWith<MoneroError> { MoneroUtils.getBlocksFromTransfers(listOf(good, bad)) }

        assertNotNull(good.tx!!.block)
        assertNull(good.tx!!.block!!.height)
    }

    @Test
    fun testGetBlocksFromTransfersEmpty() {
        assertEquals(emptyList(), MoneroUtils.getBlocksFromTransfers(emptyList()))
    }

    @Test
    fun testGetBlocksFromOutputsDedupAndOrder() {
        val block1 = MoneroBlock()
        block1.height = 100
        val block2 = MoneroBlock()
        block2.height = 200

        val tx1 = MoneroTxWallet()
        tx1.hash = "a".repeat(64)
        tx1.block = block1
        val tx2 = MoneroTxWallet()
        tx2.hash = "b".repeat(64)
        tx2.block = block2

        val o1 = MoneroOutputWallet()
        o1.tx = tx1
        val o2 = MoneroOutputWallet()
        o2.tx = tx2
        val o3 = MoneroOutputWallet() // tx shares block1 with o1
        o3.tx = tx1

        val blocks = MoneroUtils.getBlocksFromOutputs(listOf(o1, o2, o3))
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] === block1)
        assertTrue(blocks[1] === block2)
    }

    @Test
    fun testGetBlocksFromOutputsUnconfirmedRaises() {
        // unlike getBlocksFromTxs()/getBlocksFromTransfers(), an unconfirmed (blockless) output's tx does not get a
        // placeholder block -- it raises instead
        val tx = MoneroTxWallet()
        tx.hash = "a".repeat(64)
        val output = MoneroOutputWallet()
        output.tx = tx
        assertNull(output.tx!!.block)

        val e = assertFailsWith<MoneroError> { MoneroUtils.getBlocksFromOutputs(listOf(output)) }
        assertTrue(e.message!!.contains("Need to handle unconfirmed output"), e.message)
    }

    @Test
    fun testGetBlocksFromOutputsUnconfirmedRaisesAfterEarlierMatch() {
        // unlike getBlocksFromTransfers(), getBlocksFromOutputs() never mutates its inputs
        val block = MoneroBlock()
        block.height = 100
        val confirmedTx = MoneroTxWallet()
        confirmedTx.hash = "a".repeat(64)
        confirmedTx.block = block
        val good = MoneroOutputWallet()
        good.tx = confirmedTx

        val badTx = MoneroTxWallet()
        badTx.hash = "b".repeat(64) // unconfirmed
        val bad = MoneroOutputWallet()
        bad.tx = badTx

        val e = assertFailsWith<MoneroError> { MoneroUtils.getBlocksFromOutputs(listOf(good, bad)) }
        assertTrue(e.message!!.contains("Need to handle unconfirmed output"), e.message)
    }

    @Test
    fun testGetBlocksFromOutputsMissingTxRaises() {
        // same as getBlocksFromTransfers(): output.tx is a legitimate but unchecked null; must raise rather than
        // dereference
        val output = MoneroOutputWallet()
        output.amount = BigInteger.valueOf(1000000)
        val e = assertFailsWith<MoneroError> { MoneroUtils.getBlocksFromOutputs(listOf(output)) }
        assertEquals("Output has no tx", e.message)
    }

    @Test
    fun testGetBlocksFromOutputsEmpty() {
        assertEquals(emptyList(), MoneroUtils.getBlocksFromOutputs(emptyList()))
    }

    // The free() region of the python tests, including its RSS leak test, is not mirrored: free() exists only to break
    // the shared_ptr cycles of boost::python, which the garbage collector of the JVM reclaims on its own. The
    // native side of this bridge is a different matter: monero-c allocates the binary and the JSON of every
    // serialization, and only monero_utils_free() gives that memory back, so the two tests below watch what the
    // python RSS test watched, on the side that can actually leak here

    // A binding that forgets to call monero_utils_free() leaks native memory that the heap of the JVM never sees,
    // and every other test of this class stays green while it grows. Like the RSS test of the python suite, this
    // one loops a bounded batch of calls, forces a steady state, and asserts that the native memory of the process
    // stays roughly flat
    @Test
    fun testSerializationLoopDoesNotLeakNativeMemory() {
        val small = mapOf("heights" to listOf(111, 222, 333))
        val large = mapOf("msg" to (1..100).joinToString("") { "${config.serialization.msg}\n" })
        fun runBatch(rounds: Int) {
            repeat(rounds) {
                MoneroUtils.binaryToDict(MoneroUtils.dictToBinary(small))
                MoneroUtils.binaryToDict(MoneroUtils.dictToBinary(large))
            }
        }

        // unmeasured warmup: the JVM compiles the loop while it runs, and its heap and the native allocators grow
        // to their steady state, which takes longer to absorb than the one-time growth the python test warms away
        runBatch(10_000)
        System.gc()
        val before = nativeMemoryKb()
        if (before == null) {
            assumeTrue(false, "the physical memory of the process cannot be read on this platform")
            return
        }

        // measured: each round trip holds a native buffer from json_to_binary() and a native string from
        // binary_to_json(), for both maps, which the binding must give back
        val rounds = 25_000
        runBatch(rounds)
        System.gc()
        val after = nativeMemoryKb()!!

        val growthMb = (after - before) / 1024.0
        // generous bound: a binding that never frees holds back the JSON string of the large map, about 14 KB per
        // round trip, which is ~315 MB for the 25,000 round trips measured here, an order of magnitude above the
        // ~35 MB that the settling of the JVM grows by in the RSS (on Linux the malloc bytes are read instead, which stay
        // within a few MB of flat). This only needs to rule out that magnitude of leak, not pin the settling down
        assertTrue(growthMb < 128, "Memory grew by $growthMb MB after $rounds serialization round trips -- possible native leak")
    }

    // A C bool is one byte: the ABI puts a returned one in the low byte of the register alone, and a passed one is
    // 0 or 1. JNA on its own marshals a Boolean as a 32-bit number, which reads the bits that the ABI leaves
    // undefined and writes a true parameter as -1, so the library maps its Booleans through the one-byte
    // BooleanTypeMapper of MoneroNative. This test loads the library a second time through declarations that map
    // the bools to a byte directly, and asserts that the two mappings agree. They stop agreeing the day either
    // side of the bridge stops honoring the one-byte contract, which is the shape of bug that only shows on some
    // platforms
    @Test
    fun testBoolMarshallingMatchesOneByteAbi() {
        val probe = boolProbe

        // every address of the fixture, and then some, on every network: the Boolean mapping of the library and
        // the one-byte mapping of the probe must agree
        val addresses = ArrayList<String?>()
        for (book in listOf(config.mainnet, config.testnet, config.stagenet)) {
            addresses.addAll(listOf(book.primaryAddress1, book.primaryAddress2, book.primaryAddress3, book.subaddress1, book.subaddress2, book.subaddress3, book.integrated1, book.integrated2, book.integrated3, book.invalid1, book.invalid2, book.invalid3))
        }
        addresses.addAll(listOf<String?>(null, "", "not an address"))
        for (networkType in listOf(MoneroNetworkType.MAINNET, MoneroNetworkType.TESTNET, MoneroNetworkType.STAGENET)) {
            for (address in addresses) {
                val asByte = probe.monero_utils_is_valid_address(address, networkType.ordinal) != 0.toByte()
                assertEquals(asByte, MoneroUtils.isValidAddress(address, networkType), "the Boolean mapping and the one-byte mapping of isValidAddress($address, $networkType) disagree")
            }
        }

        // a by-value bool parameter crosses the bridge on the daemon calls, where JNA writes a Boolean as -1. A
        // daemon that cannot be reached parses all of its arguments before it fails at the network, so the Boolean
        // parameter of the library and the byte parameter of the probe must fail the same way, with the same error
        val connection = PointerByReference()
        MoneroNative.ok(MoneroNative.lib.monero_rpc_connection_create("""{"uri":"http://127.0.0.1:9/"}""", connection))
        val daemon = PointerByReference()
        try {
            MoneroNative.ok(MoneroNative.lib.monero_daemon_connect_with(connection.value!!, daemon))
            val hashes = arrayOf("a".repeat(64))
            for (prune in listOf(true, false)) {
                val asBoolean = assertFailsWith<MoneroError> { MoneroNative.ok(MoneroNative.lib.monero_daemon_get_txs(daemon.value!!, hashes, SizeT(1), prune, PointerByReference())) }.message
                val asByte = assertFailsWith<MoneroError> { MoneroNative.ok(probe.monero_daemon_get_txs(daemon.value!!, hashes, SizeT(1), (if (prune) 1 else 0).toByte(), PointerByReference())) }.message
                assertEquals(asByte, asBoolean, "the Boolean mapping and the one-byte mapping of the prune parameter (prune=$prune) disagree")
            }
        } finally {
            MoneroNative.lib.monero_daemon_free(daemon.value)
            MoneroNative.lib.monero_rpc_connection_free(connection.value)
        }
    }

    // the helpers of the python tests, which these tests mirror

    // like WalletUtils.build_payment_uri_config() of the python tests
    private fun buildPaymentUriConfig(address: String): MoneroTxConfig {
        val txConfig = MoneroTxConfig()
        txConfig.accountIndex = 0
        txConfig.address = address
        txConfig.amount = BigInteger.valueOf(250000000000L)
        txConfig.recipientName = "John Doe"
        txConfig.note = "My transfer to wallet"
        return txConfig
    }

    // like WalletUtils.test_invalid_address() of the python tests: the address is invalid, and validateAddress() says so
    private fun testInvalidAddress(address: String?, networkType: MoneroNetworkType) {
        val e = assertFailsWith<MoneroError>(address) { MoneroUtils.validateAddress(address, networkType) }
        assertTrue(e.message!!.contains("Invalid address"), e.message)
    }

    // like WalletUtils.test_invalid_private_view_key() of the python tests
    private fun testInvalidPrivateViewKey(privateViewKey: String?) {
        val e = assertFailsWith<MoneroError>(privateViewKey) { MoneroUtils.validatePrivateViewKey(privateViewKey) }
        assertTrue(e.message!!.contains("private view key expected to be 64 hex characters"), e.message)
    }

    // like WalletUtils.test_invalid_public_view_key() of the python tests
    private fun testInvalidPublicViewKey(publicViewKey: String?) {
        val e = assertFailsWith<MoneroError>(publicViewKey) { MoneroUtils.validatePublicViewKey(publicViewKey) }
        assertTrue(e.message!!.contains("public view key expected to be 64 hex characters"), e.message)
    }

    // like WalletUtils.test_invalid_private_spend_key() of the python tests
    private fun testInvalidPrivateSpendKey(privateSpendKey: String?) {
        val e = assertFailsWith<MoneroError>(privateSpendKey) { MoneroUtils.validatePrivateSpendKey(privateSpendKey) }
        assertTrue(e.message!!.contains("private spend key expected to be 64 hex characters"), e.message)
    }

    // like WalletUtils.test_invalid_public_spend_key() of the python tests
    private fun testInvalidPublicSpendKey(publicSpendKey: String?) {
        val e = assertFailsWith<MoneroError>(publicSpendKey) { MoneroUtils.validatePublicSpendKey(publicSpendKey) }
        assertTrue(e.message!!.contains("public spend key expected to be 64 hex characters"), e.message)
    }

    // What mallinfo2() of glibc returns: ten size_t counters of the allocator, kept as an array since only two are read
    @Structure.FieldOrder("counters")
    open class MallInfo2 : Structure() {
        @JvmField var counters = LongArray(10)

        // uordblks (bytes in use) + hblkhd (bytes in mmapped chunks)
        fun inUse() = counters[7] + counters[4]

        class ByValue : MallInfo2(), Structure.ByValue
    }

    interface GlibcMalloc : Library {
        fun mallinfo2(): MallInfo2.ByValue
    }

    // The native memory of this process in KB, or null if the platform can't tell, which skips the test.
    // On Linux it is what malloc has handed out (mallinfo2), which unlike the RSS doesn't follow the heap of the JVM, the
    // JIT or the free pages of the arenas: the RSS moved enough on arm64 to fail the leak test without a leak.
    // On macOS it is the physical footprint of vmmap, which counts the pages that the memory compressor took out of the
    // resident set: a leak of cold pages would vanish from the RSS. Elsewhere, or without mallinfo2 (musl, 32-bit), it
    // is the RSS of ps.
    private fun nativeMemoryKb(): Long? {
        if (System.getProperty("os.name").startsWith("Linux") && Native.SIZE_T_SIZE == 8) {
            try {
                return Native.load("c", GlibcMalloc::class.java).mallinfo2().inUse() / 1024
            } catch (_: UnsatisfiedLinkError) {
                // no mallinfo2() in this libc
            }
        }
        val pid = ProcessHandle.current().pid().toString()
        try {
            val vmmap = ProcessBuilder("vmmap", "--summary", pid).start()
            val summary = vmmap.inputStream.readAllBytes().decodeToString()
            vmmap.waitFor()
            val footprint = summary.lineSequence().firstOrNull { it.contains("Physical footprint:") }
            val size = footprint?.substringAfter("Physical footprint:")?.trim()
            val number = size?.takeWhile { it.isDigit() || it == '.' }?.toDoubleOrNull()
            if (number != null) {
                return when {
                    size!!.contains('G') -> (number * 1024 * 1024).toLong()
                    size.contains('M') -> (number * 1024).toLong()
                    size.contains('K') -> number.toLong()
                    else -> (number / 1024).toLong()
                }
            }
        } catch (e: IOException) {
            // no vmmap on this platform: the resident set is what is left
        }
        return try {
            val ps = ProcessBuilder("ps", "-o", "rss=", "-p", pid).start()
            ps.inputStream.readAllBytes().decodeToString().trim().toLongOrNull()
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Declarations of the two functions whose C bools cross the bridge, mapped to a byte, which is the size the ABI
     * defines for them: a returned bool is in the low byte of the register alone, and a passed one is 0 or 1. The
     * probe is loaded beside the Boolean mapping of the library, and the test checks the two against each other.
     */
    @Suppress("FunctionName")
    private interface BoolByteProbe : Library {
        fun monero_utils_is_valid_address(address: String?, networkType: Int): Byte
        fun monero_daemon_get_txs(daemon: Pointer, txHashes: Array<String>, numTxHashes: SizeT, prune: Byte, json: PointerByReference): Int
    }

    // the probe of the one-byte bool mapping, loaded from the same library as the Boolean mapping of MoneroC
    private val boolProbe: BoolByteProbe by lazy {
        val path = System.getProperty(MoneroNative.LIBRARY_PROPERTY) ?: System.getenv(MoneroNative.LIBRARY_ENVIRONMENT) ?: "monero_c"
        Native.load(path, BoolByteProbe::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
    }

    /**
     * The utils tests configuration.
     */
    class Config {
        var mainnet: AddressBook = AddressBook()
        var testnet: AddressBook = AddressBook()
        var stagenet: AddressBook = AddressBook()
        var keys: KeysBook = KeysBook()
        var serialization: Serialization = Serialization()
    }

    /**
     * An address book: the primary addresses, subaddresses, integrated addresses and invalid addresses of a network.
     */
    class AddressBook {
        var primaryAddress1: String = ""
        var primaryAddress2: String = ""
        var primaryAddress3: String = ""
        var primaryAddress4: String = ""
        var subaddress1: String = ""
        var subaddress2: String = ""
        var subaddress3: String = ""
        var subaddress4: String = ""
        var integrated1: String = ""
        var integrated2: String = ""
        var integrated3: String = ""
        var invalid1: String = ""
        var invalid2: String = ""
        var invalid3: String = ""
    }

    /**
     * The wallet keys book: the keys, the invalid keys and the seed.
     */
    class KeysBook {
        var privateViewKey: String = ""
        var publicViewKey: String = ""
        var privateSpendKey: String = ""
        var publicSpendKey: String = ""
        var invalidPrivateViewKey: String = ""
        var invalidPublicViewKey: String = ""
        var invalidPrivateSpendKey: String = ""
        var invalidPublicSpendKey: String = ""
        var seed: String = ""
    }

    /**
     * The message to serialize.
     */
    class Serialization {
        var msg: String = ""
    }
}
