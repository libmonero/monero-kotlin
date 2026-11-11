package monero

/**
 * The function of an interface that a test of its contract calls, for the test whose name is the one of the test of monero-python and does
 * not say it: `test_primary_address` calls `get_primary_address`. The tests whose name is `test` and the name of the function, with or
 * without a `With` and what the arguments are, do not need it.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Covers(val function: String)
