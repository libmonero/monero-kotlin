# Building

monero-kotlin is built with Gradle and needs a JDK 21. The build does not need monero-c, so it does not need the submodule. monero-c is needed to run the library, and to run the tests that call it.

## Building monero-kotlin from source

```sh
git clone https://github.com/libmonero/monero-kotlin.git
cd monero-kotlin
./gradlew build
```

The jar is in `build/libs`. The submodule `external/monero-c` is [monero-c](https://github.com/libmonero/monero-c), the C library that the daemon and the wallets call.

To use the library in another project, publish it to the local repository of Maven:

```sh
./gradlew publishToMavenLocal
```

See [Using monero-kotlin in your project](../README.md#using-monero-kotlin-in-your-project). For Android, see [android/README.md](../android/README.md).

## Getting monero-c

The library has to be loadable when a daemon or a wallet is made, see [Native library](native-library.md). Download it, or build it.

### Downloading monero-c

The [releases of monero-c](https://github.com/libmonero/monero-c/releases) have the library of every platform: Linux (x86_64 and arm64), Windows and macOS (arm64 and x86_64). Download the archive of your platform and unpack it. The library is `libmonero_c.so`, `libmonero_c.dylib` or `libmonero_c.dll` in the directory of the archive, which also has the libraries that it needs, e.g. libusb.

The release also has:

* `monero-native-jvm-<version>.jar`, with the library of every platform as resources, which monero-kotlin loads from the classpath.
* `monero-native-android-<version>.aar`, for Android.

Use the release that the submodule is pinned at, or a later one with the same ABI.

### Building monero-c from source

#### Linux and macOS

1. Clone the project repository with its submodules, which are big:
   ```sh
   git clone --recurse-submodules https://github.com/libmonero/monero-kotlin.git
   ```
2. Build monero-project, monero-cpp and monero-c, which takes a long time the first time:
   ```sh
   cd monero-kotlin/external/monero-c
   ./bin/build_libmonero_c.sh
   ```

The library is left in `external/monero-c/build`: `libmonero_c.so` on Linux and `libmonero_c.dylib` on macOS.

If the repository is cloned already, [bin/update_submodules.sh](../bin/update_submodules.sh) initializes the submodules.

#### Windows

1. Download and install [MSYS2](https://www.msys2.org/).
2. Press the Windows button and launch `MSYS2 MINGW64`.
3. Update packages: `pacman -Syu` and confirm at prompts.
4. Relaunch MSYS2 (if necessary) and install dependencies:
   ```
   pacman -S mingw-w64-x86_64-toolchain mingw-w64-x86_64-cmake mingw-w64-x86_64-openssl mingw-w64-x86_64-zeromq mingw-w64-x86_64-libsodium mingw-w64-x86_64-hidapi mingw-w64-x86_64-unbound mingw-w64-x86_64-protobuf mingw-w64-x86_64-libusb mingw-w64-x86_64-expat mingw-w64-x86_64-ntldd git make gettext base-devel wget
   wget https://repo.msys2.org/mingw/mingw64/mingw-w64-x86_64-icu-76.1-1-any.pkg.tar.zst
   pacman -U mingw-w64-x86_64-icu-76.1-1-any.pkg.tar.zst
   wget https://repo.msys2.org/mingw/mingw64/mingw-w64-x86_64-boost-1.87.0-3-any.pkg.tar.zst
   pacman -U mingw-w64-x86_64-boost-1.87.0-3-any.pkg.tar.zst
   ```
5. Clone the project repository with its submodules: `git clone --recurse-submodules https://github.com/libmonero/monero-kotlin.git`
6. Build monero-project, monero-cpp and monero-c:
   ```
   cd monero-kotlin/external/monero-c
   ./bin/build_libmonero_c.sh
   ```

## Gradle tasks

* `./gradlew build` builds the library and runs the unit tests.
* `./gradlew test` runs the unit tests, and `./gradlew integrationTest` the integration tests, see [Testing](testing.md).
* `./gradlew unitTests` collects the unit tests with the files they need to run without building anything, which [the CI](continuous-integration.md) uses.
* `./gradlew test jacocoTestReport` makes the report of the coverage.
* `./gradlew publishToMavenLocal` publishes the library to the local repository of Maven.

The build uses a single worker (`gradle.properties`), so it does not take the whole machine.
