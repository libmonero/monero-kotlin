# Contributing

Pull requests are welcome. If it's a big change, open an issue first so two people don't write the same thing.

## Pull requests

- One topic per pull request, started from `main`. Don't build it on another open pull request: it can't be merged before that one, and it shows that one's commits too.
- Keep it small. Three to five commits is usual. Each commit compiles and passes the tests on its own, so squash the fixups before asking for a review.
- Commit messages are `area: lowercase summary`, like `wallet: sync the full wallet with its daemon`. The areas in use are `wallet`, `daemon`, `common`, `model`, `native`, `internal`, `test`, `build`, `ci`, `docs` and `android`.
- Change the submodule pin, the workflows or `.gitmodules` in a pull request that is about that and nothing else.
- Say in the description what changed and how you tested it. A couple of lines is enough.
- Code taken from someone else's pull request gets a `Co-authored-by` trailer in its commit, and the description says where it came from.
- Codacy runs on every pull request. Fix what it reports, or say why not.

## Code

- Public API has KDoc with `@param`, `@return` and `@throws`.
- Bind the functions of monero-c as its header has them (`external/monero-c/src/*/monero_c_*.h`). Don't cite lines of the header in comments, they move whenever it changes. Name the function.
- The ABI version has to match: while the major is 0, so does the minor.
- A wallet handle is used by one thread at a time (see Thread Safety in the README of monero-c). The calls of a wallet go through its worker. The ones that can't, like `requestShutdown()`, say why in their KDoc.
- Seeds and private keys are never logged or printed, and they are freed with `monero_utils_free_secret`, which `MoneroNative.secret` does.
- Names and behavior follow `MoneroWallet` and `MoneroDaemon`, and the tests follow monero-python (`tests/test_monero_*.py`) where they have one.

## Tests

- A change comes with its tests, and a fix with a test that fails without it.
- Tests that call monero-c skip themselves when it isn't found (`NativeTestSupport.assumeAvailable()`). Tests that need a node are tagged `integration` and use `RegtestSupport`, which points them at the containers of `src/test/docker/docker-compose.yml`. Every other class of tests is tagged `unit`, and `TestTagsTest` fails if a class has neither tag.
- What every wallet has to pass is in `BaseMoneroWalletTest`, as in `test_monero_wallet_common.py` of monero-python: the test class of a wallet extends it and says how to make a wallet. A test that only one kind of wallet can pass is in the class of that wallet.
- A test that mirrors one of monero-python can carry its marks: `@NotSupported` passes while the test throws a "not supported" error, and `@NotImplemented` skips it while it fails, and says in the log when it passes, so the mark can go.
- How to run them is in [Running tests](README.md#running-tests) of the README. The Android module builds with `-Pandroid`, see [android/README.md](android/README.md).
- The Native workflow builds monero-c from the submodule, which takes a while the first time the submodule changes.
