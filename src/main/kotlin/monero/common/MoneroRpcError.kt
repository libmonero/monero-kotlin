package monero.common

/**
 * Exception when a daemon or a wallet RPC server answers a request with an error, or with an HTTP status that is not OK, such as
 * "Method not found" for a method that it does not have. It is a [MoneroError], so a catch of that one catches this too.
 */
open class MoneroRpcError : MoneroError {

    /**
     * The code of the error: the JSON-RPC error code, or the HTTP status. It is null when it is not known, which it is for every error
     * that comes from monero-c, as it reports that the server answered with an error and what it said, but not the code.
     */
    val code: Int?

    constructor(message: String?, code: Int? = null) : super(message) {
        this.code = code
    }

    constructor(message: String?, code: Int?, cause: Throwable?) : super(message, cause) {
        this.code = code
    }
}
