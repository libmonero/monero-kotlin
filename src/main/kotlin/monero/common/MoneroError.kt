package monero.common

/**
 * Exception when interacting with Monero, such as the message of a daemon or wallet that rejects a request.
 */
open class MoneroError : RuntimeException {

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
