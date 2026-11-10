# Native library

The calls of a daemon and of a wallet are made by [monero-c](https://github.com/libmonero/monero-c), a C ABI to monero-project, which monero-kotlin calls through [JNA](https://github.com/java-native-access/jna). The library is `libmonero_c.so` on Linux, `libmonero_c.dylib` on macOS and `libmonero_c.dll` on Windows. To get it, see [Building](building.md#getting-monero-c).

## Loading

The library is loaded the first time that a daemon or a wallet is made. These are tried in order:

1. The system property `monero.c.library`, or the environment variable `MONERO_C_LIBRARY`, with the path of the library:
   ```sh
   MONERO_C_LIBRARY=/path/to/monero-kotlin/external/monero-c/build/libmonero_c.so ./myapp
   ```
2. The `monero-native-jvm` jar of a release of monero-c, if it is in the classpath, e.g. `runtimeOnly(files("libs/monero-native-jvm-<version>.jar"))`. The files of your platform in it, which are the library and the libusb that it needs, are copied to a new folder in the temporary one (the one of `jna.tmpdir`, if it is set) and the library is loaded from there. The folder is deleted when the program ends, except on Windows, which does not delete a library that is loaded.
3. The library `monero_c`, as [JNA](https://github.com/java-native-access/jna) looks for it: in `jna.library.path` and in the library path of the system.

A `MoneroError` says where the library was looked for, when none of them has it.

## Version of the ABI

When the library is loaded, the version of its ABI is compared with the one that monero-kotlin was written for. While the major version is 0, the minor version has to be the same (0.3 now). A `MoneroError` is thrown if it is not, and if the library is so old that it has no version.

The version of monero-c that the submodule `external/monero-c` is pinned at is the one that the CI tests the library with. A later release with the same ABI works too.

## Memory

The library allocates the strings and the buffers that it returns, and monero-kotlin releases them: a string with `monero_utils_free`, and a secret (the seed, the private keys and the transaction keys) with `monero_utils_free_secret`, which overwrites it first. A daemon and a wallet hold a native handle, which is released when they are closed. Close them: the JVM does not release it for you. See [Threads, handles and listeners](threads.md#closing).

## Errors

A call that fails has its message in monero-c, per thread. monero-kotlin reads it right after the call, in the same thread, and throws a `MoneroError`, or a `MoneroRpcError` when the daemon or the wallet RPC server answered with an error. See [Data model](data-model.md#errors).

## Android

The library for Android is an AAR with the native library in it, which JNA finds in the `lib/<abi>` of the app. See [android/README.md](../android/README.md).
