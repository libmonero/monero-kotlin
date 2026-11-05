// The library for Android: the same sources of the JVM library, compiled against the Android SDK
// and packaged as an AAR with the native library of monero-c. The module is included only with
// -Pandroid (see settings.gradle.kts) because it needs the SDK and the cross-built library.
//
// The native library is not in this repository: it is the libmonero_c.so of the Android arm64
// build, which the android job of the Native workflow of the CI produces as the artifact
// monero-c-android-arm64 (that job and its build script are reviewed on the android-ci branch).
// Point -Pmonero.c.android.library (or the environment MONERO_C_ANDROID_LIBRARY) at it; the
// default is where the CI artifact is downloaded.
plugins {
    // no versions: both come from the classpath of the root build, where the Kotlin plugin of
    // the repository already is and the Android one is declared beside it
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "org.libmonero.android"
    compileSdk = 35

    defaultConfig {
        // The API floor of the cross-built libmonero_c.so: the bionic iconv that boost.locale
        // needs is __INTRODUCED_IN(28)
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            // the rules a consumer of the AAR needs under minification: JNA marshals through
            // reflection, which minification would strip
            consumerProguardFiles("consumer-rules.pro")
        }
    }

    sourceSets {
        getByName("main") {
            // the sources of the library, the same ones the JVM build compiles
            kotlin.srcDir("../src/main/kotlin")
            // the native library, staged by the task below
            jniLibs.srcDir(layout.buildDirectory.dir("jniLibs"))
        }
    }

    publishing {
        // the release variant is what the publication below carries
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

// where libmonero_c.so comes from: a property, an environment variable, or the CI artifact
val moneroCLibrary = providers.gradleProperty("monero.c.android.library")
    .orElse(providers.environmentVariable("MONERO_C_ANDROID_LIBRARY"))
    .orElse("${rootProject.projectDir}/external/monero-c/dist/libmonero_c.so")

// stages the native library in the layout of jniLibs: arm64-v8a/libmonero_c.so
val stageMoneroCLibrary by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("jniLibs/arm64-v8a"))
    from(moneroCLibrary.map { File(it) }) {
        rename { "libmonero_c.so" }
    }
    doFirst {
        val library = File(moneroCLibrary.get())
        if (!library.isFile) {
            throw GradleException(
                "libmonero_c.so is not at $library: set -Pmonero.c.android.library (or the environment " +
                    "MONERO_C_ANDROID_LIBRARY) to the Android arm64 build of it, which the android job of " +
                    "the CI builds (see the android-ci branch)"
            )
        }

        // the library has to be an arm64 ELF for Android: a library of another architecture
        // carries the same names, so the checks below would pass and the app would die at its
        // first dlopen. The magic is at the head of the file and the machine at offset 18.
        library.inputStream().use { input ->
            val header = ByteArray(20)
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n < 0) throw GradleException("$library is shorter than an ELF header")
                read += n
            }
            if (!(header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() && header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte())) {
                throw GradleException("$library is not an ELF library")
            }
            val machine = (header[19].toInt() and 0xFF shl 8) or (header[18].toInt() and 0xFF)
            if (machine != 0xB7) {
                throw GradleException(
                    "$library is not an arm64 library (e_machine 0x" + machine.toString(16) +
                        "): stage the Android arm64 build of monero-c"
                )
            }
        }

        // the light wallet of the sources needs the light functions of the C ABI, which the
        // submodule pin has but a monero-c release does not: an AAR without them loads fine and
        // fails on the phone with an UnsatisfiedLinkError, so check before packaging. The
        // exported names live in the dynamic symbol table, whose strings are in the file
        // (ISO_8859_1 keeps the bytes one to one).
        val lightFunctions = listOf(
            "monero_wallet_light_create", "monero_wallet_light_open",
            "monero_wallet_light_wallet_exists", "monero_wallet_light_get_unrefined_spend_count"
        )
        val found = HashSet<String>()
        library.inputStream().use { input ->
            val chunk = ByteArray(1 shl 20)
            val overlap = 128
            var carry = ByteArray(0)
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                val window = ByteArray(carry.size + n)
                carry.copyInto(window)
                chunk.copyInto(window, carry.size, 0, n)
                val text = String(window, Charsets.ISO_8859_1)
                for (function in lightFunctions) if (text.indexOf(function) >= 0) found.add(function)
                carry = window.copyOfRange(maxOf(0, window.size - overlap), window.size)
            }
        }
        val missing = lightFunctions.filter { it !in found }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "$library has no light wallet functions (${missing.joinToString()}): the library needs a " +
                    "monero-c with the light wallet surface of the C ABI, which the submodule pin has; " +
                    "build it from the light wallet branch of monero-c and point " +
                    "-Pmonero.c.android.library at that build"
            )
        }
    }
}

// the staging feeds the jniLibs merge of both variants
tasks.matching { it.name == "mergeDebugJniLibFolders" || it.name == "mergeReleaseJniLibFolders" }
    .configureEach { dependsOn(stageMoneroCLibrary) }

dependencies {
    // the same dependencies the JVM library declares, at the same versions
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    // JNA's Android artifact: it carries the jni libraries of every ABI, the plain jar of the JVM
    // library carries the desktop ones
    implementation("net.java.dev.jna:jna:5.15.0@aar")
}

publishing {
    publications {
        register<MavenPublication>("android") {
            group = rootProject.group.toString()
            artifactId = "monero-kotlin-android"
            version = rootProject.version.toString()
            afterEvaluate { from(components["release"]) }
        }
    }
}
