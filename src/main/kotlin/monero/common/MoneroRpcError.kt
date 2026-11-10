package monero.common

/**
 * Exception when a daemon or a wallet RPC server answers a request with an error, or with an HTTP status that is not OK, such as
 * "Method not found" for a method that it does not have. It is a [MoneroError], so a catch of that one catches this too.
 */
open class MoneroRpcError : MoneroError {

    /**
     * The code of the error: the JSON-RPC error code, or the HTTP status of an answer that is not OK. It is null when the error has no
     * code, such as an answer that is not JSON-RPC and has no HTTP status to report.
     */
    val code: Int?

    constructor(message: String?, code: Int? = null) : super(message) {
        this.code = code
    }

    constructor(message: String?, code: Int?, cause: Throwable?) : super(message, cause) {
        this.code = code
    }
}
