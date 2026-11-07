package monero

/**
 * Marks a test whose operation is not implemented yet, like `not_implemented` in monero-python, which is a non-strict xfail.
 * While the test fails it is reported as skipped, with the failure as the reason, and once it passes it passes and says in the
 * log that the mark can go. On a class it applies to every test of it. [TestMarkersExtension] does it.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class NotImplemented
