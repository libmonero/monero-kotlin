package monero

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.StreamHandler

/**
 * The log of the tests, the way the tests of monero-python have it: what they run goes to the console at INFO, and to a file at DEBUG.
 * The directory of the files is build/test-logs, or the one that the environment variable MONERO_KOTLIN_TEST_LOG_DIR names. The log that
 * monero-c writes is in the same directory, see [NativeTestSupport].
 */
object TestLog {

    const val DIRECTORY_ENVIRONMENT = "MONERO_KOTLIN_TEST_LOG_DIR"

    val directory: File = File(System.getenv(DIRECTORY_ENVIRONMENT) ?: "build/test-logs")

    private val root: Logger = Logger.getLogger("monero.tests").also { root ->
        root.useParentHandlers = false
        root.level = Level.FINE
        root.addHandler(ConsoleHandler().also { it.level = Level.INFO })
        try {
            directory.mkdirs()
            root.addHandler(FileHandler(File(directory, "monero_tests_kotlin.log").path, false).also {
                it.formatter = LineFormatter()
                it.encoding = "UTF-8"
                it.level = Level.FINE
            })
        } catch (e: IOException) {
            // the tests run without the file
            root.warning("The log file of the tests can not be written in $directory: $e")
        }
    }

    /**
     * Get the logger of a test class, which is named as the class.
     */
    fun logger(name: String): Logger {
        return Logger.getLogger("${root.name}.$name")
    }

    // the console of the tests is the standard output, which Gradle and the console launcher both show, and closing it is not for a handler
    private class ConsoleHandler : StreamHandler(System.out as OutputStream, LineFormatter()) {
        override fun publish(record: LogRecord) {
            super.publish(record)
            flush()
        }

        override fun close() {
            flush()
        }
    }

    // 2026-11-06 02:30:00 INFO     MoneroUtilsTest Before testSetLogLevel
    private class LineFormatter : Formatter() {
        override fun format(record: LogRecord): String {
            val level = when {
                record.level.intValue() >= Level.SEVERE.intValue() -> "ERROR"
                record.level.intValue() >= Level.WARNING.intValue() -> "WARNING"
                record.level.intValue() >= Level.INFO.intValue() -> "INFO"
                else -> "DEBUG"
            }
            val name = record.loggerName.substringAfterLast('.')
            val thrown = record.thrown?.let { "\n" + it.stackTraceToString().trimEnd() } ?: ""
            return String.format("%1\$tF %1\$tT %2\$-8s %3\$s %4\$s%5\$s%n", record.millis, level, name, formatMessage(record), thrown)
        }
    }
}
