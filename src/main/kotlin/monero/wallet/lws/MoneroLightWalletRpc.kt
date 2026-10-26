package monero.wallet.lws

import java.math.BigInteger
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import monero.common.MoneroError
import monero.common.MoneroJson
import monero.wallet.lws.model.MoneroLwsAddressInfo
import monero.wallet.lws.model.MoneroLwsTransaction
import monero.wallet.lws.wire.MoneroLwsAddressInfoResponse
import monero.wallet.lws.wire.MoneroLwsAddressRequest
import monero.wallet.lws.wire.MoneroLwsAddressTxsResponse
import monero.wallet.lws.wire.MoneroLwsLoginRequest
import monero.wallet.lws.wire.MoneroLwsLoginResponse
import monero.wallet.lws.wire.MoneroLwsSubmitRawTxRequest
import monero.wallet.lws.wire.MoneroLwsSubmitRawTxResponse
import monero.wallet.lws.wire.MoneroLwsTx
import monero.wallet.lws.wire.MoneroLwsVersion

/**
 * Implements a [MoneroLightWalletClient] against the REST API of a [monero-lws](https://github.com/vtnerd/monero-lws)
 * server, with the http client of the JDK. Close it to release the http client.
 *
 * Every request carries the address and the view key of the account, which is all the authentication the server has:
 * it rejects a request with a bare 403 when it has no account for the address, and it answers a bare 500 when the
 * request fails, which the captures show for a view key that does not match the address. Both come with an empty
 * body. The server writes the amounts of its responses as unsigned 64-bit numbers in decimal strings, and it leaves a
 * field out entirely when there is nothing to write, which the wire models read.
 *
 * The client gives the server ten seconds to take a connection and thirty to answer a request, so a server that goes
 * silent becomes a MoneroError instead of a call that never returns.
 *
 * @param uri is the URI of the light wallet server, without the path of an endpoint
 * @param address is the address of the account to scan through the server
 * @param viewKey is the private view key of the account
 * @param createAccount is whether a login asks the server to create the account when it has none for the address
 * @param generatedLocally is whether the wallet generated the keys of the account itself, which a login tells the server
 * @throws MoneroError if the uri is not a valid http or https URI
 */
class MoneroLightWalletRpc(
    uri: String,
    private val address: String,
    private val viewKey: String,
    private val createAccount: Boolean = true,
    private val generatedLocally: Boolean = false
) : MoneroLightWalletClient {

    private val server: URI
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()
    private var closed = false

    init {
        server = try {
            URI(uri)
        } catch (e: URISyntaxException) {
            throw MoneroError("The uri of the light wallet server is not valid: $uri", e)
        }
        val scheme = server.scheme
        if (scheme != "http" && scheme != "https") {
            throw MoneroError("The uri of the light wallet server is not an http or https uri: $uri")
        }
    }

    override suspend fun login(): MoneroLwsLoginResponse {
        val request = MoneroLwsLoginRequest()
        request.address = address
        request.viewKey = viewKey
        request.createAccount = createAccount
        request.generatedLocally = generatedLocally
        return MoneroJson.deserialize(json("/login", post("/login", request)), MoneroLwsLoginResponse::class.java)
    }

    override suspend fun getAddressInfo(): MoneroLwsAddressInfo {
        val response = MoneroJson.deserialize(json("/get_address_info", post("/get_address_info", addressRequest())), MoneroLwsAddressInfoResponse::class.java)
        val received = response.totalReceived ?: BigInteger.ZERO
        val sent = response.totalSent ?: BigInteger.ZERO
        val locked = response.lockedFunds ?: BigInteger.ZERO
        val info = MoneroLwsAddressInfo()
        info.totalBalance = received.subtract(sent)
        info.unlockedBalance = received.subtract(sent).subtract(locked)
        info.scannedHeight = response.scannedHeight
        info.startHeight = response.startHeight
        return info
    }

    override suspend fun getAddressTxs(): List<MoneroLwsTransaction> {
        val response = MoneroJson.deserialize(json("/get_address_txs", post("/get_address_txs", addressRequest())), MoneroLwsAddressTxsResponse::class.java)
        return response.transactions.orEmpty().map { tx -> toTransaction(tx) }
    }

    override suspend fun submitRawTx(signedTxHex: String): Boolean {
        val request = MoneroLwsSubmitRawTxRequest()
        request.tx = signedTxHex
        val response = post("/submit_raw_tx", request)
        if (response.statusCode() == 422) return false
        return MoneroJson.deserialize(json("/submit_raw_tx", response), MoneroLwsSubmitRawTxResponse::class.java).status == "OK"
    }

    override suspend fun getVersion(): MoneroLwsVersion {
        // the version endpoint takes an empty object, which is what the captures of the request bodies sent
        return MoneroJson.deserialize(json("/get_version", post("/get_version", emptyMap<String, Any>())), MoneroLwsVersion::class.java)
    }

    override fun close() {
        closed = true
        http.close()
    }

    /**
     * A transaction as the server sends it becomes one as the wallet shows it. The amount of an incoming transaction
     * is what it received, and of an outgoing one what it sent plus its fee, which the server always writes: the
     * wallet does not have to guess with the view key. A transaction in the mempool has no height and no time.
     *
     * @param tx is the transaction as the server sends it
     * @return the transaction as the wallet shows it
     * @throws MoneroError if the time of the transaction is not the ISO-8601 time of the server
     */
    private fun toTransaction(tx: MoneroLwsTx): MoneroLwsTransaction {
        val transaction = MoneroLwsTransaction()
        transaction.hash = tx.hash
        val received = tx.totalReceived ?: BigInteger.ZERO
        val sent = tx.totalSent ?: BigInteger.ZERO
        transaction.isIncoming = sent.signum() == 0
        transaction.actualAmount = if (sent.signum() == 0) received else sent.add(tx.fee ?: BigInteger.ZERO)
        transaction.height = tx.height
        transaction.timestamp = tx.timestamp?.let { text ->
            try {
                Instant.parse(text).toEpochMilli()
            } catch (e: DateTimeParseException) {
                throw MoneroError("The light wallet server sent a time of a transaction that is not ISO-8601: $text", e)
            }
        }
        return transaction
    }

    private fun addressRequest(): MoneroLwsAddressRequest {
        val request = MoneroLwsAddressRequest()
        request.address = address
        request.viewKey = viewKey
        return request
    }

    /**
     * Posts a request body to a path of the server, as the JSON of a wire model. The errors that the endpoints share
     * are thrown here: the server authenticates a request with the address and the view key that the body carries, it
     * rejects it with a bare 403 when it has no account for the address, and it answers a bare 500 when the request
     * fails, which the captures show for a view key that does not match the address. Both come with an empty body.
     *
     * @param path is the path of the endpoint to call
     * @param requestBody is the body of the request, which a wire model holds
     * @return the response of the server
     * @throws MoneroError if the client is closed, or the server rejects the credentials
     */
    private suspend fun post(path: String, requestBody: Any): HttpResponse<String> {
        if (closed) throw MoneroError("The light wallet client is closed")
        val request = HttpRequest.newBuilder(server.resolve(path))
            .header("Content-Type", "application/json")
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(MoneroJson.serialize(requestBody)))
            .build()
        val response = send(request)
        when (response.statusCode()) {
            403 -> throw MoneroError("The light wallet server has no account for the address: $address")
            500 -> throw MoneroError("The light wallet server answered 500 on $path: the view key does not match the address $address, or the server failed")
        }
        return response
    }

    /**
     * Reads the body of a response that has to be a 200.
     *
     * @param path is the path of the request that the response answers
     * @param response is the response of the server
     * @return the JSON of the response
     * @throws MoneroError if the server did not answer 200
     */
    private fun json(path: String, response: HttpResponse<String>): String {
        val status = response.statusCode()
        if (status != 200) {
            val body = response.body()
            throw MoneroError("The light wallet server answered $status on $path with: ${body.ifEmpty { "an empty body" }}")
        }
        return response.body()
    }

    /**
     * Sends a request with the http client of the JDK, which is asynchronous, and resumes the call with the response.
     *
     * @param request is the request to send
     * @return the response of the server
     * @throws MoneroError if the server does not answer, or it does not answer in time
     */
    private suspend fun send(request: HttpRequest): HttpResponse<String> {
        return suspendCoroutine { continuation ->
            http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete { response, error ->
                if (error == null) continuation.resume(response) else continuation.resumeWithException(failure(error))
            }
        }
    }

    // the error of a request that the server never answered: a timeout names the time that the request overran, and
    // anything else names a server that is not there
    private fun failure(error: Throwable): MoneroError {
        val cause = error.cause ?: error
        return if (cause is HttpTimeoutException) {
            MoneroError("The light wallet server at $server did not answer within ${REQUEST_TIMEOUT.toMillis()} ms", error)
        } else {
            MoneroError("The light wallet server at $server is not reachable: ${cause.message}", error)
        }
    }

    private companion object {

        // how long the client gives the server to take a connection, and to answer a request
        private val CONNECT_TIMEOUT = Duration.ofSeconds(10)
        private val REQUEST_TIMEOUT = Duration.ofSeconds(30)
    }
}
