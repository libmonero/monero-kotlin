// The library for Android: the sources of the JVM library, compiled against the Android SDK and packaged as an AAR with the native library of
// monero-c. The module is included only with -Pandroid (see settings.gradle.kts) because it needs the SDK and the native library.
//
// The native library is not in the repository. monero-c builds it for Android and releases it as monero-native-android-<version>.aar, with
// libmonero_c.so for arm64-v8a (devices) and x86_64 (the emulator). Point -Pmonero.c.android.native (or the environment variable
// MONERO_C_ANDROID_NATIVE) at that archive, or at a folder with <abi>/libmonero_c.so.
plugins {
    // no versions: the Kotlin plugin is on the classpath of the root build, and the Android one is added there with -Pandroid
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
        // The API floor of libmonero_c.so for Android: the bionic iconv that boost.locale needs came with Android 9
        minSdk = 28

        // the rules that an app needs when it minifies
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// where the native library comes from: a property or an environment variable
val moneroCNative: File? = providers.gradleProperty("monero.c.android.native")
    .orElse(providers.environmentVariable("MONERO_C_ANDROID_NATIVE"))
    .orNull
    ?.let { file(it) }

// fails with where to get the native library, since a build without it would give an AAR that loads and then dies on the phone
val requireMoneroC by tasks.registering {
    doLast {
        if (moneroCNative == null || !moneroCNative.exists()) {
            throw GradleException(
                "The native library of monero-c is not at ${moneroCNative ?: "any place"}: set -Pmonero.c.android.native (or the " +
                    "environment variable MONERO_C_ANDROID_NATIVE) to monero-native-android-<version>.aar from a release of monero-c, " +
                    "or to a folder with arm64-v8a/libmonero_c.so and x86_64/libmonero_c.so"
            )
        }
        val libraries = if (moneroCNative.isFile) {
            zipTree(moneroCNative).matching { include("jni/*/libmonero_c.so") }.files
        } else {
            fileTree(moneroCNative) { include("*/libmonero_c.so") }.files
        }
        if (libraries.isEmpty()) {
            throw GradleException("$moneroCNative has no <abi>/libmonero_c.so (or jni/<abi>/libmonero_c.so in an archive)")
        }
    }
}

// stages the native library in the layout of jniLibs, <abi>/libmonero_c.so, from the archive of monero-c or from a folder with that layout
val stageMoneroC by tasks.registering(Sync::class) {
    dependsOn(requireMoneroC)
    into(layout.buildDirectory.dir("jniLibs"))
    includeEmptyDirs = false
    if (moneroCNative != null && moneroCNative.isFile) {
        from(zipTree(moneroCNative)) {
            include("jni/*/libmonero_c.so")
            // jni/<abi>/libmonero_c.so is <abi>/libmonero_c.so
            eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        }
    } else if (moneroCNative != null) {
        from(moneroCNative) {
            include("*/libmonero_c.so")
        }
    }
}

// the staging feeds the merge of the native libraries of every variant
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach { dependsOn(stageMoneroC) }

dependencies {
    // the same dependencies the JVM library declares, at the same versions
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    // the Android artifact of JNA: it carries the native libraries of JNA for every ABI of Android, where the jar of the JVM library
    // carries the ones of the desktop
    implementation("net.java.dev.jna:jna:5.15.0@aar")
}

publishing {
    publications {
        register<MavenPublication>("android") {
            groupId = rootProject.group.toString()
            artifactId = "monero-kotlin-android"
            version = rootProject.version.toString()
            afterEvaluate { from(components["release"]) }
        }
    }
}
