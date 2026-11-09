package monero.internal

import com.sun.jna.Platform
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Tag

@Tag("unit")
class NativeBundleTest {

    @TempDir
    lateinit var tempDir: Path

    private fun jar(vararg files: Pair<String, String>): File {
        val jar = tempDir.resolve("native-${System.nanoTime()}.jar").toFile()
        JarOutputStream(jar.outputStream()).use { out ->
            for ((name, content) in files) {
                out.putNextEntry(JarEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return jar
    }

    // the class loader has only what it is given, and not the jars of the tests
    private fun loaderOf(file: File): URLClassLoader = URLClassLoader(arrayOf(file.toURI().toURL()), null)

    private fun target(): Path = Files.createDirectory(tempDir.resolve("out-${System.nanoTime()}"))

    private fun names(directory: Path): List<String> = directory.toFile().list()!!.sorted()

    // The jar of monero-c has the library and its libusb in the folder of each platform
    @Test
    fun extract_FromAJar_CopiesTheFilesOfThePlatform() {
        val jar = jar(
            "linux-x86-64/libmonero_c.so" to "library",
            "linux-x86-64/libusb-1.0.so.0" to "usb",
            "linux-aarch64/libmonero_c.so" to "another platform",
            "linux-x86-64/sub/other" to "in a folder of the folder",
            "META-INF/monero-native/LICENSE" to "license"
        )
        val directory = target()

        val library = loaderOf(jar).use { NativeBundle.extract(it, "linux-x86-64", "libmonero_c.so", directory) }

        assertEquals(directory.resolve("libmonero_c.so").toFile(), library)
        assertEquals(listOf("libmonero_c.so", "libusb-1.0.so.0"), names(directory))
        assertEquals("library", directory.resolve("libmonero_c.so").toFile().readText())
        assertEquals("usb", directory.resolve("libusb-1.0.so.0").toFile().readText())
    }

    // A jar can name an entry with the parent in it or with a backslash, which the copy would write
    // outside the folder of the platform on windows: only a plain file name is taken
    @Test
    fun extract_FromAJarWithEntriesThatEscapeTheFolder_TakesOnlyThePlainFileNames() {
        val jar = jar(
            "linux-x86-64/libmonero_c.so" to "library",
            "linux-x86-64/.." to "the parent",
            "linux-x86-64/." to "the folder",
            "linux-x86-64/..\\libusb-1.0.so.0" to "outside the folder",
            "linux-x86-64/sub/other" to "in a folder of the folder"
        )
        val directory = target()

        loaderOf(jar).use { NativeBundle.extract(it, "linux-x86-64", "libmonero_c.so", directory) }

        assertEquals(listOf("libmonero_c.so"), names(directory))
    }

    // A folder of the classpath is the same, e.g. the resources of a build
    @Test
    fun extract_FromADirectory_CopiesTheFilesOfThePlatform() {
        val resources = Files.createDirectories(tempDir.resolve("resources/darwin-aarch64"))
        resources.resolve("libmonero_c.dylib").toFile().writeText("library")
        resources.resolve("libusb-1.0.0.dylib").toFile().writeText("usb")
        Files.createDirectories(tempDir.resolve("resources/linux-x86-64")).resolve("libmonero_c.so").toFile().writeText("another platform")
        val directory = target()

        val library = URLClassLoader(arrayOf(tempDir.resolve("resources").toUri().toURL()), null).use {
            NativeBundle.extract(it, "darwin-aarch64", "libmonero_c.dylib", directory)
        }

        assertEquals(directory.resolve("libmonero_c.dylib").toFile(), library)
        assertEquals(listOf("libmonero_c.dylib", "libusb-1.0.0.dylib"), names(directory))
    }

    // Without the library in the folder of the platform there is nothing to take
    @Test
    fun extract_WithoutTheLibraryOfThePlatform_ReturnsNull() {
        val jar = jar("linux-aarch64/libmonero_c.so" to "another platform", "linux-x86-64/libusb-1.0.so.0" to "usb")
        val directory = target()

        loaderOf(jar).use {
            assertNull(NativeBundle.extract(it, "linux-x86-64", "libmonero_c.so", directory))
        }
        assertEquals(emptyList(), names(directory))
        loaderOf(jar("README" to "nothing")).use { assertNull(NativeBundle.extract(it)) }
    }

    // The folder of the platform and the file of the library are those of this system
    @Test
    fun extract_ByDefault_UsesThePlatformOfTheSystem() {
        val library = System.mapLibraryName("monero_c")
        val jar = jar("${Platform.RESOURCE_PREFIX}/$library" to "library", "${Platform.RESOURCE_PREFIX}/libusb" to "usb")
        val directory = target()

        val extracted = loaderOf(jar).use { NativeBundle.extract(it, directory = directory) }

        assertNotNull(extracted)
        assertEquals(directory.resolve(library).toFile(), extracted)
        assertEquals("library", extracted.readText())
    }
}
