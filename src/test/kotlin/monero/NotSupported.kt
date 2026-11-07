package monero

/**
 * Marks a test whose operation is permanently unsupported by the type that runs it, like `not_supported` in monero-python.
 * The test passes when it throws an error that says that the operation is not supported, and fails when it throws any other
 * error or none. On a class it applies to every test of it. [TestMarkersExtension] does it.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class NotSupported
