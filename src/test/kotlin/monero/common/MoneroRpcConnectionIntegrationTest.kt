package monero.common

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.RegtestSupport
import org.junit.jupiter.api.Tag

/**
 * Mirrors the tests of the RPC connection of monero-python (tests/test_monero_rpc_connection.py) that need a server: the
 * check of the connection to a node and to a wallet RPC server, and the three kinds of request. They need monero-c and the regtest
 * node and wallet RPC server that integration-tests.yml starts with the docker-compose.yml of monero-c. The ones that need no
 * server are in [MoneroRpcConnectionTest].
 */
@Tag("integration")
class MoneroRpcConnectionIntegrationTest {

    private val timeoutMs = 15000L

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    private fun nodeConnection() = MoneroRpcConnection(RegtestSupport.daemonUri).also { it.timeoutMs = timeoutMs }

    // the check that python's RpcConnectionUtils.test_rpc_connection does
    private fun assertCheckConnection(connection: MoneroRpcConnection, uri: String) {
        assertEquals(uri, connection.uri)
        assertNull(connection.isConnected())

        // the first check finds the server, and the second finds nothing new
        assertTrue(connection.checkConnection())
        assertFalse(connection.checkConnection())
        assertEquals(true, connection.isConnected())
        assertEquals(true, connection.isOnline)
        assertEquals(true, connection.isAuthenticated)
        val responseTime = assertNotNull(connection.responseTime)
        assertTrue(responseTime > 0, "The response time is $responseTime")
        assertFalse(connection.isI2p())
        assertFalse(connection.isOnion())
    }

    // Test monerod rpc connection
    @Test
    fun testNodeRpcConnection() {
        nodeConnection().use { assertCheckConnection(it, RegtestSupport.daemonUri) }
    }

    // Test wallet rpc connection
    @Test
    fun testWalletRpcConnection() {
        RegtestSupport.assumeWalletRpcAvailable()
        MoneroRpcConnection(RegtestSupport.walletRpcUri).also { it.timeoutMs = timeoutMs }.use { assertCheckConnection(it, RegtestSupport.walletRpcUri) }
    }

    // Can set credentials: the node has no login, so it accepts any
    @Test
    fun testSetCredentials() {
        MoneroRpcConnection(RegtestSupport.daemonUri).use { connection ->
            assertNull(connection.username)
            assertNull(connection.password)

            connection.setCredentials("rpc_daemon_user", "abc123")
            assertNull(connection.isOnline)
            assertNull(connection.isAuthenticated)
            assertNull(connection.isConnected())
            assertEquals("rpc_daemon_user", connection.username)
            assertEquals("abc123", connection.password)

            assertTrue(connection.checkConnection(), "Could not check connection")
            assertEquals(true, connection.isOnline)
            assertEquals(true, connection.isAuthenticated)
            assertEquals(true, connection.isConnected())

            // empty credentials
            connection.setCredentials("", "")
            assertNull(connection.isOnline)
            assertNull(connection.isConnected())
            assertNull(connection.username)
            assertNull(connection.password)
        }
    }

    // Can send json request
    @Test
    fun testSendJsonRequest() {
        nodeConnection().use { connection ->
            val result = connection.sendJsonRequest("get_version")
            assertTrue(""""version"""" in result, result)

            // the parameters are sent
            val header = MoneroJson.mapper.readTree(connection.sendJsonRequest("get_block_header_by_height", mapOf("height" to 0)))
            assertEquals(0, header.path("block_header").path("height").asInt(-1), header.toString())

            // test invalid json rpc method
            val error = assertFailsWith<MoneroError> { connection.sendJsonRequest("invalid_method") }
            assertEquals("Method not found", error.message)
        }
    }

    // Can send binary request
    @Test
    fun testSendBinaryRequest() {
        nodeConnection().use { connection ->
            val binary = connection.sendBinaryRequest("get_blocks_by_height.bin", mapOf("heights" to listOf(0)))
            assertTrue(binary.isNotEmpty())
            val json = MoneroUtils.binaryBlocksToJson(binary)
            assertTrue(""""blocks"""" in json, json)

            // test invalid binary method
            val error = assertFailsWith<MoneroError> { connection.sendBinaryRequest("invalid_method") }
            assertTrue("404" in error.message!!, error.message)
        }
    }

    // Can send path request
    @Test
    fun testSendPathRequest() {
        nodeConnection().use { connection ->
            val result = connection.sendPathRequest("get_height")
            assertTrue(""""height"""" in result, result)

            // test invalid path method
            val error = assertFailsWith<MoneroError> { connection.sendPathRequest("invalid_method") }
            assertTrue("404" in error.message!!, error.message)
        }
    }
}
