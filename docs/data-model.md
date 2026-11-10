# Data model

The data model of monero-kotlin is the one of [monero-cpp](https://github.com/woodser/monero-cpp) and [monero-java](https://github.com/woodser/monero-java), in Kotlin types. The [data model and API specification](../README.md#documentation) lists every class and function. This page is about how the model behaves.

## Packages

| Package | What it has |
| --- | --- |
| `monero.common` | `MoneroError`, `MoneroRpcError`, `MoneroRpcConnection`, `MoneroUtils` and `MoneroJson` |
| `monero.daemon` | the `MoneroDaemon` interface, `MoneroDaemonDefault` and `MoneroDaemonRpc` |
| `monero.daemon.model` | the models of the daemon: blocks, headers, transactions, peers, the pool and the rest |
| `monero.wallet` | the `MoneroWallet` interface, `MoneroWalletDefault`, `MoneroWalletFull`, `MoneroWalletKeys` and `MoneroWalletRpc` |
| `monero.wallet.model` | the models of a wallet: transactions, transfers, outputs, accounts, subaddresses, queries and configs |

## Types

* **Amounts** are in atomic units, as `BigInteger`. The 64-bit numbers of monero-c are unsigned, and they are read as such: a balance above `Long.MAX_VALUE` is not a negative number.
* **Heights**, timestamps and sizes are `Long`, and indices are `Int`.
* **Properties that may not be known** are nullable. A property of a model is `null` when the value is not known, e.g. the height of a transaction that is in the pool.
* **Enumerations** are Kotlin enums. In JSON they are numbers, as monero-cpp writes them.

## Interfaces

Daemon implementations are interchangeable by conforming to [MoneroDaemon](../src/main/kotlin/monero/daemon/MoneroDaemon.kt), and the wallets to [MoneroWallet](../src/main/kotlin/monero/wallet/MoneroWallet.kt).

`MoneroDaemonDefault` and `MoneroWalletDefault` are the abstract bases: they write the functions that are the same for every implementation with the ones that they call. What an implementation does not override throws a `MoneroError` that says `<function> is not supported`, so an implementation has the functions that it can do and nothing else. A keys-only wallet, for example, has no daemon to ask.

## Errors

* `MoneroError` is the exception of the library, for a bad argument, a closed wallet or daemon, and a call of monero-c that failed. It is a `RuntimeException`.
* `MoneroRpcError` is a `MoneroError` for a daemon or a wallet RPC server that answered with an error, or with an HTTP status that is not OK. Its `code` is the JSON-RPC code or the HTTP status when it is known. It is `null` for the errors that come from monero-c, which reports that the server answered with an error and what it said, but not the code.

```kotlin
try {
    daemon.getBlockByHeight(height)
} catch (e: MoneroRpcError) {
    // the server answered with an error
} catch (e: MoneroError) {
    // any other error of the library
}
```

## JSON

[MoneroJson](../src/main/kotlin/monero/common/MoneroJson.kt) reads and writes the models as the JSON of monero-cpp and monero-c: the names of the properties, a property that is not set is left out, and the enumerations are numbers. The models are read and written through their fields, and a property that is not known is ignored.

```kotlin
val block = MoneroJson.deserialize<MoneroBlock>(json)
val fee = block.txs?.first()?.fee      // atomic units
val written = MoneroJson.serialize(block)
```

The JSON that the unit tests read is in [src/test/resources/fixtures](../src/test/resources/fixtures).

## Queries

A query is a model with the fields of the thing that it queries, and the ones that are set are the criteria: the things that do not meet them are filtered out. A query that sets nothing meets everything.

```kotlin
val query = MoneroTxQuery()
query.isConfirmed = true
query.isIncoming = true
query.minHeight = 1000
val txs = wallet.getTxs(query)
```

`MoneroTxQuery`, `MoneroTransferQuery` and `MoneroOutputQuery` have `meetsCriteria()`, which says whether a transaction, a transfer or an output meets the query, so a query can also be used on the things that a program already has.

A `MoneroTxQuery` can hold a `transferQuery`, an `inputQuery` and an `outputQuery`. A transaction meets them when at least one of its transfers, inputs or outputs meets the query. `queryChildren` decides whether they are checked, and `queryParent` of a transfer or an output decides whether the query of its transaction is checked too.

## Merging

The models of a wallet are merged when the same thing is seen twice, such as a transaction that a wallet saw in the pool and then in a block. It is the reconciliation of monero-cpp:

* A field that is set on one side takes the value of that side.
* The same value on both sides is that value.
* Two values that differ are an error (`Cannot reconcile values`), unless the field is one that can change. A flag that can become true and not go back (a transaction that is confirmed) takes the preferred value, a number that can only grow (a height, the confirmations) the higher one, and one that can only go down (the time at which a transaction was first received) the lower one.

## Differences from monero-java

The model is the one of monero-cpp, which parts from monero-java in three places. Code that is ported from monero-java should know them:

* **A transaction that is not in a block yet does not meet a `minHeight`.** It has no height, so a query with a `minHeight` filters it out, where monero-java does not filter the unconfirmed. `height` and `maxHeight` filter it out in both.
* **A transaction whose direction is not known yet meets neither `isIncoming = true` nor `isIncoming = false`** in a `MoneroTxQuery`, and the same for `isOutgoing`. monero-java reads an unknown direction as false, so there the transaction meets `false`.
* **A transaction whose `isIncoming` differs between two copies is an error.** monero-java resolves it to incoming, since the outputs of a transaction that a wallet sent are seen when it is confirmed.

These three are pinned by the tests of the queries and of the merge, in [src/test/kotlin/monero/wallet](../src/test/kotlin/monero/wallet).

## RPC connections

A [MoneroRpcConnection](../src/main/kotlin/monero/common/MoneroRpcConnection.kt) is the configuration of a connection to a daemon or to a wallet RPC server: the URI, the credentials, a proxy, the priority, the timeout and the verification of the certificate. It is also what is known of the server after `checkConnection()`: whether it is online and authenticated, and its response time.

```kotlin
val connection = MoneroRpcConnection("http://localhost:38081", "superuser", "abctesting123")
connection.proxyUri = "<proxy uri>"       // e.g. the local proxy of a Tor node
val daemon = MoneroDaemonRpc(connection)

connection.checkConnection()
val online = connection.isOnline
```

A connection can send requests of its own too: `sendJsonRequest()`, `sendPathRequest()` and `sendBinaryRequest()`. `isOnion()` and `isI2p()` say whether the URI is of one of those networks.

The functions that talk to the server use a handle of monero-c, which is made the first time that one is called. If a field of the configuration changes after that, the next call makes a new handle. `close()` releases the handle, and the connection can be used after that.

The proxy is part of the configuration that the handle of monero-c is made with. There is no test in this repository that goes through a proxy, so check yours before relying on it.
