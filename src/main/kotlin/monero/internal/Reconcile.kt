package monero.internal

import monero.common.MoneroError

/**
 * How the models merge the fields of two copies of the same thing, such as a transaction that a wallet saw in the pool and then in a
 * block. It is the reconciliation of monero-cpp: a field that is set on one side takes the value of that side, the same value on both
 * sides is that value, and two values that differ are an error unless the field is one that can change, which then has its rule.
 */
internal object Reconcile {

    /**
     * Reconcile a field that does not change.
     *
     * @param what names the field, for the error
     * @throws MoneroError if both are set and they are not the same
     */
    fun <T : Any> value(a: T?, b: T?, what: String): T? {
        if (a == b) return a
        if (a == null) return b
        if (b == null) return a
        throw MoneroError("Cannot reconcile values: $a vs $b. $what")
    }

    /**
     * Reconcile a flag that can change to one value, and not back: a transaction can become confirmed, but it does not stop being
     * so. When the two differ it is [preferred].
     */
    fun flag(a: Boolean?, b: Boolean?, preferred: Boolean): Boolean? {
        if (a == b) return a
        if (a == null) return b
        if (b == null) return a
        return preferred
    }

    /**
     * Reconcile a number that can only grow, such as a height or the number of confirmations: it is the higher one.
     */
    fun max(a: Long?, b: Long?): Long? {
        if (a == null) return b
        if (b == null) return a
        return maxOf(a, b)
    }

    /**
     * Reconcile a number that can only go down, such as the time at which a transaction was first received: it is the lower one.
     */
    fun min(a: Long?, b: Long?): Long? {
        if (a == null) return b
        if (b == null) return a
        return minOf(a, b)
    }

    /**
     * Reconcile a list. A list that is null or empty is not set.
     *
     * @throws MoneroError if both are set and they are not the same
     */
    fun <T> list(a: List<T>?, b: List<T>?, what: String): List<T>? {
        if (a == b) return a
        if (a.isNullOrEmpty()) return b ?: a
        if (b.isNullOrEmpty()) return a
        throw MoneroError("Cannot reconcile lists. $what")
    }
}
