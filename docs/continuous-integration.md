# Continuous integration

The workflows are in [.github/workflows](../.github/workflows), and run on every pull request and on main. Codacy checks the static analysis and the coverage of every pull request too.

| Workflow | What it does |
| --- | --- |
| [Build](../.github/workflows/build.yml) | Builds the library and the unit tests, and uploads the unit tests, with the files they need to run, and the agent and the command line of JaCoCo for the coverage (`./gradlew unitTests`). |
| [Native](../.github/workflows/native.yml) | Builds monero-c from the submodule `external/monero-c` on Linux (x86_64 and arm64), Windows and macOS (arm64 and x86_64), with the recipes of the Build workflows of monero-c. |
| [Unit tests](../.github/workflows/unit-tests.yml) | Waits for the Build and Native runs of the same commit, and runs the unit tests that Build made on the same platforms, with the library that Native built, without building anything. |
| [Android](../.github/workflows/android.yml) | Builds the android module and the example application, with the `monero-native-android` archive of the release of monero-c that the submodule is pinned at, and uploads the AAR. |
| [Integration tests](../.github/workflows/integration-tests.yml) | Waits for the same runs, starts the nodes and the wallet RPC servers, and runs the tests tagged `integration` on Linux x86_64. |
| [Codacy Coverage Reporter](../.github/workflows/codacy.yml) | Sends the reports of the coverage of Unit tests and Integration tests to Codacy, which merges them, when both have ended, for pull requests and for main. |

## Native

The library is kept in the cache of Actions with the commit of the submodule in the key, so it is built again only when the submodule changes. Its compilation, dependencies included, is cached with ccache, which only the runs on main save.

When the submodule moves to another release of monero-c, the Android workflow has to move with it: it fails if the release that it downloads is not the one of the submodule. Set `MONERO_C_VERSION` and `MONERO_C_ANDROID_SHA256` in `android.yml` to the release, and the sha256 of its `monero-native-android` archive from the `SHA256SUMS.txt` of the release.

## Unit tests

On Linux x86_64 the unit tests run with the agent of JaCoCo that Build uploaded with them, and the workflow uploads the report of the coverage.

## Integration tests

The workflow starts the nodes and the wallet RPC servers of `src/test/docker/docker-compose.yml`, see [Testing](testing.md#integration-tests). It fails if it finds no integration test, and uploads the logs of the containers. It runs the tests with the agent of JaCoCo as well, and uploads the report of the coverage.

## Coverage

Unit tests and Integration tests each upload the coverage of their tests. The two end at different times, so the end of each one starts the Codacy Coverage Reporter, and the one that finds the other ended sends both. Codacy merges them with one command: a line that both cover counts once.
