package monero

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import monero.common.MoneroError
import org.junit.jupiter.api.Assumptions.abort
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * What the integration tests share: the regtest node that integration-tests.yml starts with the docker-compose.yml of
 * monero-c, and the mining that moves its chain. Set the environment variable MONERO_C_TEST_DAEMON_URI to the URI of the
 * node when it is not the one of that file.
 */
object RegtestSupport {

    val daemonUri: String = System.getenv("MONERO_C_TEST_DAEMON_URI") ?: "http://127.0.0.1:18081"

    // the primary address of the test wallet of monero-python's config.ini, which the mining calls pay by default
    const val MINING_ADDRESS = "48W9YHwPzRz9aPTeXCA6kmSpW6HsvmWx578jj3of2gT3JwZzwTf33amESBoNDkL6SVK34Q2HTKqgYbGyE1hBws3wCrcBDR2"

    private val http by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build() }

    /**
     * Skip the test if monero-c can not be loaded, or no node answers at the URI.
     */
    fun assumeAvailable() {
        NativeTestSupport.assumeAvailable()
        assumeTrue(isDaemonUp(), "no monerod at $daemonUri: start the compose of monero-c, or set MONERO_C_TEST_DAEMON_URI")
        startChain()
    }

    /**
     * Mine a block if the chain is only its genesis block. A wallet that is created on such a chain keeps the height
     * that it found, which is the one that it has scanned, so a sync after the blocks that the test mines for it
     * fetches nothing.
     */
    private fun startChain() {
        val request = HttpRequest.newBuilder(URI.create(daemonUri + "/get_height")).timeout(Duration.ofSeconds(2)).GET().build()

        // a node that goes away after the check of assumeAvailable skips the test, it does not fail it
        val body = try {
            http.send(request, HttpResponse.BodyHandlers.ofString()).body()
        } catch (e: java.io.IOException) {
            abort("the node at $daemonUri went away: $e")
        }
        val height = Regex("\"height\":\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toLong()
        if (height != null && height <= 1) mineBlocks(1)
    }

    fun isDaemonUp(): Boolean {
        return try {
            val request = HttpRequest.newBuilder(URI.create(daemonUri + "/get_height")).timeout(Duration.ofSeconds(2)).GET().build()
            http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200
        } catch (_: java.io.IOException) {
            false
        }
    }

    /**
     * Mine blocks on the regtest chain.
     *
     * @param numBlocks is the number of blocks to mine
     * @param address is the address that the blocks pay
     * @throws MoneroError if the daemon does not mine them
     */
    fun mineBlocks(numBlocks: Int, address: String = MINING_ADDRESS) {
        val body = """{"jsonrpc": "2.0", "id": "0", "method": "generateblocks",""" +
            """ "params": {"amount_of_blocks": $numBlocks, "wallet_address": "$address", "starting_nonce": 0}}"""
        val request = HttpRequest.newBuilder(URI.create(daemonUri + "/json_rpc"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(180))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200 || !response.body().contains("\"status\": \"OK\"")) {
            throw MoneroError("generateblocks did not mine: ${response.body()}")
        }
    }
}
