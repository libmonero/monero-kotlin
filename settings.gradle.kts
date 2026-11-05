rootProject.name = "monero-kotlin"

// The android module needs the Android SDK and the native library of monero-c for Android, which the builds of the JVM library do not have:
// it is included only when it is asked for with -Pandroid, so everything else (the CI of the JVM library, a machine without the SDK) stays
// as it is
if (providers.gradleProperty("android").isPresent) {
    include(":android")
}
