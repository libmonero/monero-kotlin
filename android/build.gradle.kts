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
    from(moneroCLibrary.map { file(it) }) {
        rename { "libmonero_c.so" }
    }
    doFirst {
        val library = file(moneroCLibrary.get())
        if (!library.isFile) {
            throw GradleException(
                "libmonero_c.so is not at $library: set -Pmonero.c.android.library (or the environment " +
                    "MONERO_C_ANDROID_LIBRARY) to the Android arm64 build of it, which the android job of " +
                    "the CI builds (see the android-ci branch)"
            )
        }

        // the light wallet of the sources needs the light functions of the C ABI, which the
        // monero-c of the submodule pin does not have yet: an AAR without them loads fine and
        // fails on the phone with an UnsatisfiedLinkError, so check before packaging. The
        // exported names live in the dynamic symbol table, whose strings are in the file
        // (ISO_8859_1 keeps the bytes one to one).
        val text = String(library.readBytes(), Charsets.ISO_8859_1)
        val lightFunctions = listOf(
            "monero_wallet_light_create", "monero_wallet_light_open",
            "monero_wallet_light_wallet_exists", "monero_wallet_light_get_unrefined_spend_count"
        )
        val missing = lightFunctions.filter { function -> text.indexOf(function) < 0 }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "$library has no light wallet functions (${missing.joinToString()}): the library needs a " +
                    "monero-c with the light wallet surface of the C ABI, which the submodule pin does not " +
                    "have yet — build it from the light wallet branch of monero-c and point " +
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
