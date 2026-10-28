# Android

The library for Android: an AAR with the same sources of the JVM library, compiled against
the Android SDK, plus the native library of monero-c for arm64-v8a.

The module is not part of the default build, because it needs the Android SDK and the
cross-built native library: add it with `-Pandroid`.

## The native library

`libmonero_c.so` is not in this repository. It comes from the Android arm64 build of monero-c:

- in the CI, the android job of the `Native` workflow builds it and uploads it as the artifact
  `monero-c-android-arm64` — that job, its build script and the cmake patches it applies are
  reviewed on the `android-ci` branch of this repository;
- locally, cross-build it with the NDK for arm64-v8a: configure monero-c and its dependencies
  with the toolchain of the NDK (`ANDROID_ABI=arm64-v8a`, `ANDROID_PLATFORM=android-28`) and
  `-fPIC` for the static dependencies, then strip the result with `llvm-strip --strip-unneeded`;
  the build script on the `android-ci` branch does exactly this and is the reference recipe.

Point the build at it with `-Pmonero.c.android.library=<path>` (or the environment
variable `MONERO_C_ANDROID_LIBRARY`); the default is `external/monero-c/dist/libmonero_c.so`,
where the CI artifact is downloaded. The file is the stripped one: strip a local build with
`llvm-strip --strip-unneeded` first, or the AAR carries the debug information too.

## Build and publish

```
ANDROID_HOME=<sdk> ./gradlew -Pandroid :android:assembleRelease
ANDROID_HOME=<sdk> ./gradlew -Pandroid :android:publishToMavenLocal
```

Published as `org.libmonero:monero-kotlin-android` at the version of the library, with the
dependencies of the POM (Jackson and JNA's Android artifact). An Android app consumes it
with:

```kotlin
dependencies {
    implementation("org.libmonero:monero-kotlin-android:<version>")
}
```

The library loads on Android unchanged: `MoneroNative` asks JNA for `monero_c`, and JNA
resolves it from the `lib/arm64-v8a` of the app, where the AAR put it.

## Requirements

- Android SDK with the platform 35 (compile) and build-tools; `ANDROID_HOME` set or
  `sdk.dir` in `local.properties`.
- `minSdk` 28: the native library needs the bionic iconv of Android 9.
