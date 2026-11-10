# Threads, handles and listeners

The calls of monero-kotlin are blocking. The call itself is made by [monero-c](https://github.com/libmonero/monero-c), whose C ABI allows one thread at a time on a handle. This is how the library keeps to that, and what it means for the code that uses it.

## One thread for each handle

A daemon ([MoneroDaemonRpc](../src/main/kotlin/monero/daemon/MoneroDaemonRpc.kt)) and a wallet ([MoneroWalletFull](../src/main/kotlin/monero/wallet/MoneroWalletFull.kt), [MoneroWalletKeys](../src/main/kotlin/monero/wallet/MoneroWalletKeys.kt) and [MoneroWalletRpc](../src/main/kotlin/monero/wallet/MoneroWalletRpc.kt)) each hold a native handle and a thread of their own, the worker. A call runs on the worker, one after the other, and the thread that made it waits for the result. So a call does not depend on the thread that makes it: any thread can call a wallet, and the calls are in a queue.

```kotlin
// two threads can use the same wallet: the calls run one after the other
thread { wallet.getBalance() }
thread { wallet.getTxs() }
```

* An error of the call is thrown to the thread that made it, as it is.
* The stack of the worker is 8 MB, as the main thread of Linux, since monero-cpp parses JSON by recursion and the default of a thread of the JVM is 1 MB.
* A call that is made from the worker itself would wait for itself, since the worker runs one call at a time. It fails with a `MoneroError` that says so.
* A thread that is interrupted while it waits for a call gives up the wait: the call is dropped if it did not start, the interrupt stays in the flag of the thread, and a `MoneroError` is thrown. A call that is running is not stopped.

A sync keeps the worker of a wallet for as long as it runs, so the other calls of the wallet wait for it. The one call that any thread can make meanwhile is `requestShutdown()`, which asks a running sync, or a request to the daemon, to stop. The calls that need the daemon fail from then on, so close the wallet after it.

```kotlin
val syncing = thread { wallet.sync() }
// ...
wallet.requestShutdown()    // the sync stops
syncing.join()
wallet.close()
```

## Closing

A handle is released when the wallet or the daemon is closed. They are `AutoCloseable`, so `use` closes them:

```kotlin
MoneroWalletFull.createWallet(config).use { wallet ->
    // ...
    wallet.save()
}
```

* `use` closes a wallet without saving it, so `save()` it first. `close(true)` saves it before closing it.
* A keys-only wallet has no files, so closing it with a save is an error and the wallet stays open.
* The close runs after the calls that are in the queue, and waits for them. A sync that is running has to be stopped with `requestShutdown()` from another thread first.
* The wait is not interruptible: freeing the handle under a call that is still queued would crash. An interrupt of the thread that closes is kept in its flag, and the close returns when the calls have ended.
* Every call of a closed wallet fails with `Wallet is closed`, and of a closed daemon with `The daemon is closed`. Closing again does nothing.
* Closing a [MoneroWalletRpc](../src/main/kotlin/monero/wallet/MoneroWalletRpc.kt) closes its wallet on the server. A server that has no wallet open has nothing to close, and that is not an error.
* Closing a daemon releases the threads that wait for a block header, with an error.

## Listeners

A listener is called on a thread of its own, not on the thread of monero-c nor on the worker. The callbacks of monero-c only queue what they get, and another thread calls the listener, one notification after the other and in the order that they were made. So a notification can call the wallet, which waits for the sync that is running.

* A notification that does not return holds the next ones, and the close of the wallet and the sync that caused them.
* A listener that throws is skipped for that notification, and the next ones are made.

### Wallet listeners

```kotlin
wallet.addListener(object : MoneroWalletListener() {
    override fun onNewBlock(height: Long) {
        // ...
    }
    override fun onOutputReceived(output: MoneroOutputWallet) {
        // ...
    }
})
```

A listener gets the progress of a sync, the new blocks, the changes of the balances and the outputs that the wallet receives and spends. `removeListener` delivers the notifications that were made before it returns. A call that makes the wallet notify its listeners, such as a sync, waits until they have been notified of all of it before it returns.

`sync(startHeight, listener)` takes a listener for that sync only, which is not registered with the wallet. It has been notified of all of the sync when the call returns, unless it finishes no notification for a minute: then the call fails with a `MoneroError`, though the wallet has synced all the same. A slow listener that keeps finishing notifications is waited for, however many there are. A listener that finishes none for a minute is stuck (it never returns from the one it has, or it waits on a lock of the thread that called the sync), and the call would wait for it for ever.

### Daemon listeners

```kotlin
daemon.addListener(object : MoneroDaemonListener() {
    override fun onBlockHeader(header: MoneroBlockHeader) {
        // ...
    }
})
```

The native listener is added to the daemon only while there is a listener or a thread that waits for a block.

### Waiting for a block

| Call | Waits on | Meanwhile |
| --- | --- | --- |
| `daemon.waitForNextBlockHeader()` | the thread that calls | the other calls of the daemon run. It has no timeout, so it can not be cancelled except by an interrupt, which throws a `MoneroError`. |
| `daemon.waitForNextBlockHeader(timeoutMs)` | the thread that calls | the other calls of the daemon run, but `close()` waits for it. Returns `null` if no block arrived. |
| `wallet.waitForNextBlock(timeoutMs)` | the worker of the wallet | every other call of the wallet, and `close()`, wait for it. The wallet has to be syncing in the background (`startSyncing()`), or no block comes. Returns `null` if no block arrived. |

A caller that waits in a loop with a timeout can be cancelled between the waits. Pick the timeout with `close()` in mind.

## Syncing in the background

`wallet.startSyncing(syncPeriodMs)` starts a thread of the wallet that syncs it with its daemon, over and over, and `wallet.stopSyncing()` stops it. It succeeds while the wallet is not connected, and syncs when a daemon becomes reachable. `syncPeriodMs` is the most time that the wallet waits between two syncs.
