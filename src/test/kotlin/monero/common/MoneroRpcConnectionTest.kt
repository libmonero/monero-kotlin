package monero.common

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import monero.NativeTestSupport
import org.junit.jupiter.api.Tag

/**
 * Mirrors the tests of the RPC connection of monero-python (tests/test_monero_rpc_connection.py) that need no server: the
 * type of the URI, the serialization, the verification of the certificate, the copy, the credentials, the attributes and a
 * server that does not answer. The ones that need a node are in [MoneroRpcConnectionIntegrationTest].
 *
 * Not ported: the comparison of the priorities (test_priority), which is a function of the connection manager of python and not
 * of monero-c.
 */
@Tag("unit")
class MoneroRpcConnectionTest {

    // the URIs and the credentials of the nodes of the python tests, which are only strings here
    private val nodeUri = "http://127.0.0.1:18081"
    private val offlineServerUri = "offline_server_uri"

    @BeforeTest
    fun requireLibrary() {
        NativeTestSupport.assumeAvailable()
    }

    // Validate uri type
    @Test
    fun testValidateUri() {
        MoneroRpcConnection(nodeUri).use { connection ->
            assertFalse(connection.isOnion())
            assertFalse(connection.isI2p())

            // onion uri
            connection.uri = "http://moneronkvv2hu2anvcc5b4qd5y7strnc2ob6khqsrtikmhocyvjpdjyd.onion:18089"
            assertTrue(connection.isOnion())
            assertFalse(connection.isI2p())

            // i2p uri
            connection.uri = "http://2rcznulrxqlygrk2qumpwkbonnh72fujukk5nmrcuuzrkl5rn5ta.b32.i2p:18081"
            assertFalse(connection.isOnion())
            assertTrue(connection.isI2p())

            // empty uri
            connection.uri = ""
            assertFalse(connection.isOnion())
            assertFalse(connection.isI2p())

            // null uri
            connection.uri = null
            assertFalse(connection.isOnion())
            assertFalse(connection.isI2p())
        }
    }

    // Test rpc connection json serialization
    @Test
    fun testRpcConnectionSerialization() {
        val node = MoneroRpcConnection(nodeUri, "rpc_daemon_user", "abc123")
        node.timeoutMs = 15000
        node.use {
            assertEquals("""{"uri":"http://127.0.0.1:18081","username":"rpc_daemon_user","password":"abc123","priority":0,"timeoutMs":15000,"sslVerify":true}""", it.serialize())
        }

        val wallet = MoneroRpcConnection("http://127.0.0.1:18082", "rpc_user", "abc123")
        wallet.timeoutMs = 15000
        wallet.use {
            assertEquals("""{"uri":"http://127.0.0.1:18082","username":"rpc_user","password":"abc123","priority":0,"timeoutMs":15000,"sslVerify":true}""", it.serialize())
        }

        // empty connection
        MoneroRpcConnection().use { assertEquals("""{"priority":0,"sslVerify":true}""", it.serialize()) }

        // connection with every field
        val connection = MoneroRpcConnection("test_node:18081", "user", "abc123")
        connection.proxyUri = "127.0.0.1:9050"
        connection.zmqUri = "test_node:18084"
        connection.priority = 1
        connection.timeoutMs = 1000
        connection.use {
            assertEquals("""{"uri":"test_node:18081","username":"user","password":"abc123","proxyUri":"127.0.0.1:9050","zmqUri":"test_node:18084","priority":1,"timeoutMs":1000,"sslVerify":true}""", it.serialize())
        }
    }

    // The handle, the lock and the configuration of the handle are not part of the JSON of the fields
    @Test
    fun testJsonOfTheFieldsHasNoHandle() {
        MoneroRpcConnection(nodeUri).use { connection ->
            connection.isOnion()
            assertEquals("""{"uri":"http://127.0.0.1:18081"}""", MoneroJson.serialize(connection))
        }
    }

    // Can disable the verification of the TLS certificate
    @Test
    fun testSslVerify() {
        // verified by default
        val connection = MoneroRpcConnection("https://node.example:18081")
        connection.use {
            assertTrue(""""sslVerify":true""" in it.serialize())

            // can be disabled, the setting is serialized and copied
            it.sslVerify = false
            assertTrue(""""sslVerify":false""" in it.serialize())
            MoneroRpcConnection(it).use { copy -> assertEquals(false, copy.sslVerify) }
            assertEquals(false, MoneroJson.deserialize(MoneroJson.serialize(it), MoneroRpcConnection::class.java).sslVerify)
        }

        // connections that don't mention it stay verified
        MoneroJson.deserialize("""{"uri":"https://node.example:18081"}""", MoneroRpcConnection::class.java).use {
            assertTrue(""""sslVerify":true""" in it.serialize())
        }
    }

    // Can copy a rpc connection
    @Test
    fun testConnectionCopy() {
        val connection = MoneroRpcConnection(nodeUri, "rpc_daemon_user", "abc123")
        connection.timeoutMs = 15000
        connection.use { original ->
            original.copy().use { copy ->
                assertEquals(original.serialize(), copy.serialize())
            }
            MoneroRpcConnection(original).use { copy -> assertEquals(original.serialize(), copy.serialize()) }
        }
    }

    // A copy has no attributes, as the attributes are kept in memory by the connection
    @Test
    fun testCopyHasNoAttributes() {
        MoneroRpcConnection(nodeUri).use { original ->
            original.setAttribute("key", "value")
            original.copy().use { copy -> assertEquals("", copy.getAttribute("key")) }
            assertEquals("value", original.getAttribute("key"))
        }
    }

    // Test invalid connection
    @Test
    fun testInvalidConnection() {
        MoneroRpcConnection(offlineServerUri).use { connection ->
            assertEquals(offlineServerUri, connection.uri)
            assertNull(connection.isConnected())

            // the first check finds that it is offline, and the second finds nothing new
            assertTrue(connection.checkConnection(1000))
            assertFalse(connection.checkConnection(1000))
            assertEquals(false, connection.isConnected())
            assertEquals(false, connection.isOnline)
            assertNull(connection.responseTime)
            assertFalse(connection.isOnion())
            assertFalse(connection.isI2p())
        }
    }

    // Can set credentials
    @Test
    fun testSetCredentials() {
        MoneroRpcConnection(offlineServerUri).use { connection ->
            // connection without credentials
            assertNull(connection.username)
            assertNull(connection.password)

            connection.setCredentials("rpc_daemon_user", "abc123")
            assertEquals("rpc_daemon_user", connection.username)
            assertEquals("abc123", connection.password)
            assertTrue(""""username":"rpc_daemon_user"""" in connection.serialize())

            // the state of a server is not the one of other credentials
            connection.checkConnection(1000)
            assertEquals(false, connection.isOnline)
            connection.setCredentials("", "")
            assertNull(connection.isOnline)
            assertNull(connection.isConnected())

            // empty credentials
            assertNull(connection.username)
            assertNull(connection.password)
            assertFalse("username" in connection.serialize())
        }
    }

    // Test invalid credentials
    @Test
    fun testSetInvalidCredentials() {
        MoneroRpcConnection(nodeUri, "rpc_daemon_user", "abc123").use { connection ->
            val noUsername = assertFailsWith<MoneroError> { connection.setCredentials("", "abc123") }
            assertTrue("username cannot be empty because password is not empty" in noUsername.message!!, noUsername.message)
            val noPassword = assertFailsWith<MoneroError> { connection.setCredentials("user", "") }
            assertTrue("password cannot be empty because username is not empty" in noPassword.message!!, noPassword.message)

            // the credentials are the ones from before
            assertEquals("rpc_daemon_user", connection.username)
            assertEquals("abc123", connection.password)

            connection.setCredentials("user", "abc123")
            assertEquals("user", connection.username)
            assertEquals("abc123", connection.password)
        }
    }

    // Can get and set arbitrary key/value attributes
    @Test
    fun testSetAttributes() {
        MoneroRpcConnection(nodeUri).use { connection ->
            for (i in 0 until 100) assertEquals("", connection.getAttribute("attr$i"))

            val attributes = HashMap<String, String>()
            for (i in 0 until 100) {
                val value = java.util.UUID.randomUUID().toString()
                attributes["attr$i"] = value
                connection.setAttribute("attr$i", value)
            }

            for ((key, value) in attributes) assertEquals(value, connection.getAttribute(key))
        }
    }

    // A change of the configuration makes a new handle, which has no attributes and no state
    @Test
    fun testChangeOfTheConfigurationStartsOver() {
        MoneroRpcConnection(offlineServerUri).use { connection ->
            connection.setAttribute("key", "value")
            connection.checkConnection(1000)
            assertEquals(false, connection.isOnline)

            connection.uri = "other_offline_server_uri"
            assertEquals("", connection.getAttribute("key"))
            assertNull(connection.isOnline)
            assertTrue(""""uri":"other_offline_server_uri"""" in connection.serialize())
        }
    }

    // A connection can be used after it was closed, and closing it twice does nothing
    @Test
    fun testCloseAndUseAgain() {
        val connection = MoneroRpcConnection(nodeUri)
        connection.setAttribute("key", "value")
        connection.close()
        connection.close()
        assertEquals("", connection.getAttribute("key"))
        connection.close()
    }

    // A connection that was never used has no handle to close
    @Test
    fun testCloseWithoutUse() {
        MoneroRpcConnection(nodeUri).close()
    }

    // The timeout is the time to wait, from 0 to 4294967295 ms
    @Test
    fun testInvalidTimeout() {
        MoneroRpcConnection(offlineServerUri).use { connection ->
            assertFailsWith<MoneroError> { connection.checkConnection(-1) }
            assertFailsWith<MoneroError> { connection.checkConnection(4294967296L) }
            assertFailsWith<MoneroError> { connection.sendJsonRequest("get_version", timeoutMs = -1) }
        }
    }

    // Several threads can use a connection, and close() waits for them
    @Test
    fun testSeveralThreads() {
        val connection = MoneroRpcConnection(nodeUri)
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        try {
            val results = (0 until threads).map { thread ->
                pool.submit(Callable {
                    start.await()
                    repeat(200) { i ->
                        connection.setAttribute("thread$thread", "$i")
                        assertEquals("$i", connection.getAttribute("thread$thread"))
                        assertFalse(connection.isOnion())
                    }
                    true
                })
            }
            start.countDown()
            results.forEach { assertTrue(it.get(60, TimeUnit.SECONDS)) }
        } finally {
            pool.shutdownNow()
            connection.close()
        }
    }
}
