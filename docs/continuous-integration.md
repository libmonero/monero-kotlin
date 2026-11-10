# Continuous integration

The workflows are in [.github/workflows](../.github/workflows), and run on every pull request and on main. Codacy checks the static analysis and the coverage of every pull request too.

| Workflow | What it does |
| --- | --- |
| [Build](../.github/workflows/build.yml) | Builds the library and the unit tests, and uploads the unit tests, with the files they need to run, and the agent and the command line of JaCoCo for the coverage (`./gradlew unitTests`). |
| [Native](../.github/workflows/native.yml) | Downloads the library of the release of monero-c that the submodule `external/monero-c` is pinned at, for Linux (x86_64 and arm64), Windows and macOS (arm64 and x86_64), and checks its sha256. It builds nothing. |
| [Unit tests](../.github/workflows/unit-tests.yml) | Waits for the Build and Native runs of the same commit, and runs the unit tests that Build made on the same platforms, with the library that Native downloaded, without building anything. |
| [Android](../.github/workflows/android.yml) | Builds the android module and the example application, with the `monero-native-android` archive of the release of monero-c that the submodule is pinned at, and uploads the AAR. |
| [Integration tests](../.github/workflows/integration-tests.yml) | Waits for the same runs, starts the nodes and the wallet RPC servers, and runs the tests tagged `integration` on Linux x86_64. |
| [Docs](../.github/workflows/docs.yml) | Checks the links between the documents with [bin/check_docs.py](../bin/check_docs.py): the files and the headings that they name have to be there. Links to other sites are not checked. It runs only when a document changes. |
| [Codacy Coverage Reporter](../.github/workflows/codacy.yml) | Sends the reports of the coverage of Unit tests and Integration tests to Codacy, which merges them, when both have ended, for pull requests and for main. |

## Documents

Build, Native, Unit tests, Integration tests and Android ignore a push or a pull request that changes only documents: `**.md`, `docs/**`, `LICENSE` and `donate.png`. The paths are the same in the five, on purpose: Unit tests and Integration tests wait for the Build and Native runs of the commit, and would fail if only one of them was skipped. Change them in all five. Docs runs on exactly those files, so a pull request of documents has a check, and the link checker can be run by hand: `python3 bin/check_docs.py`.

A pull request that changes documents and code runs everything.

If the branch gets required checks, do not require the ones that are skipped for documents: a check that does not run stays pending, and blocks the pull request.

## Native

The library is the one in the archive of the release, so a run takes a minute, where building monero-c took hours when the submodule changed. The workflow fails if the release that it downloads is not the one of the submodule, so the submodule has to be at a release of monero-c: to test a commit that is not released, build monero-c and set `MONERO_C_LIBRARY` (see [Native library](native-library.md)).

When the submodule moves to another release of monero-c, the Native and Android workflows have to move with it. Set `MONERO_C_VERSION` in `native.yml` and `android.yml` to the release, the sha256 of each archive in `native.yml`, and `MONERO_C_ANDROID_SHA256` in `android.yml`, from the `SHA256SUMS.txt` of the release.

## Unit tests

On Linux x86_64 the unit tests run with the agent of JaCoCo that Build uploaded with them, and the workflow uploads the report of the coverage.

## Integration tests

The workflow starts the nodes and the wallet RPC servers of `src/test/docker/docker-compose.yml`, see [Testing](testing.md#integration-tests). It fails if it finds no integration test, and uploads the logs of the containers. It runs the tests with the agent of JaCoCo as well, and uploads the report of the coverage.

## Coverage

Unit tests and Integration tests each upload the coverage of their tests. The two end at different times, so the end of each one starts the Codacy Coverage Reporter, and the one that finds the other ended sends both. Codacy merges them with one command: a line that both cover counts once.
