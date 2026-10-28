pluginManagement {
    repositories {
        // The google repository hosts the Android Gradle plugin that the root build declares
        // for the android module; the rest resolves where it did
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "monero-kotlin"

// The android module needs the Android SDK and the cross-built libmonero_c.so, which the builds
// of the library do not have: it is included only when it is asked for with -Pandroid, so
// everything else (the CI of the JVM library, machines without a SDK) stays exactly as it is.
// The example needs the module, so it comes with it.
if (providers.gradleProperty("android").isPresent) {
    include(":android")
    include(":examples:android")
}
