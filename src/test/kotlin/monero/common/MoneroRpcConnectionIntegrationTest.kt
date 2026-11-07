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
 * node and wallet RPC server that integration-tests.yml starts with the docker-compose.yml of src/test/docker, which have a login. The ones that need no
 * server are in [MoneroRpcConnectionTest].
 */
@Tag("integration")
class MoneroRpcConnectionIntegrationTest {

    private val timeoutMs = 15000L

    @BeforeTest
    fun requireLibraryAndDaemon() {
        RegtestSupport.assumeAvailable()
    }

    private fun nodeConnection() = RegtestSupport.daemonConnection().also { it.timeoutMs = timeoutMs }

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
        RegtestSupport.walletRpcConnection().also { it.timeoutMs = timeoutMs }.use { assertCheckConnection(it, RegtestSupport.walletRpcUri) }
    }

    // Can set credentials
    @Test
    fun testSetCredentials() {
        MoneroRpcConnection(RegtestSupport.daemonUri).use { connection ->
            // connection without credentials
            assertNull(connection.username)
            assertNull(connection.password)

            // the node has a login, so it does not accept the connection
            assertTrue(connection.checkConnection())
            assertEquals(true, connection.isOnline)
            assertEquals(false, connection.isAuthenticated)
            assertEquals(false, connection.isConnected())

            connection.setCredentials(RegtestSupport.daemonUsername, RegtestSupport.daemonPassword)
            assertNull(connection.isOnline)
            assertNull(connection.isAuthenticated)
            assertNull(connection.isConnected())
            assertEquals(RegtestSupport.daemonUsername, connection.username)
            assertEquals(RegtestSupport.daemonPassword, connection.password)

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

    // Test invalid credentials
    @Test
    fun testSetInvalidCredentials() {
        nodeConnection().use { connection ->
            val noUsername = assertFailsWith<MoneroError> { connection.setCredentials("", "abc123") }
            assertTrue("username cannot be empty because password is not empty" in noUsername.message!!, noUsername.message)
            assertFalse(noUsername is MoneroRpcError)
            val noPassword = assertFailsWith<MoneroError> { connection.setCredentials("user", "") }
            assertTrue("password cannot be empty because username is not empty" in noPassword.message!!, noPassword.message)

            // the credentials are the ones from before
            assertEquals(RegtestSupport.daemonUsername, connection.username)
            assertEquals(RegtestSupport.daemonPassword, connection.password)

            // other credentials reach the node, which does not accept them
            connection.setCredentials("user", "abc123")
            assertEquals("user", connection.username)
            assertEquals("abc123", connection.password)
            assertTrue(connection.checkConnection())
            assertEquals(true, connection.isOnline)
            assertEquals(false, connection.isAuthenticated)
            assertEquals(false, connection.isConnected())
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
            val error = assertFailsWith<MoneroRpcError> { connection.sendJsonRequest("invalid_method") }
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
            val error = assertFailsWith<MoneroRpcError> { connection.sendBinaryRequest("invalid_method") }
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
            val error = assertFailsWith<MoneroRpcError> { connection.sendPathRequest("invalid_method") }
            assertTrue("404" in error.message!!, error.message)
        }
    }
}
