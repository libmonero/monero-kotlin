package monero.wallet

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import monero.NotSupported
import monero.common.MoneroRpcConnection
import monero.daemon.model.MoneroKeyImage
import monero.wallet.model.MoneroTxConfig
import monero.wallet.model.MoneroTxSet
import monero.wallet.model.MoneroTxWallet
import monero.wallet.model.MoneroWalletListener
import org.junit.jupiter.api.Tag

/**
 * Mirrors the tests of the MoneroWallet interface of monero-python (test_monero_wallet_interface.py), which call each function of the base class and
 * expect the error that says that it is not supported. MoneroWalletDefault does that for the functions that an implementation does not
 * override, so these call an instance of it that overrides none. The functions that it implements with the ones that they call, e.g.
 * a function with a list of one element, call a function of the interface and so end in that error too: what they pass to it is the
 * test of MoneroWalletDefaultTest.
 */
@Tag("unit")
class MoneroWalletInterfaceTest {

    private val wallet: MoneroWallet = object : MoneroWalletDefault() {}

    // a function of the interface without a test here is a function whose default nobody has tried
    @Test
    fun everyFunctionOfTheInterface_HasATest() {
        val tested = MoneroWalletInterfaceTest::class.java.declaredMethods.filter { it.isAnnotationPresent(NotSupported::class.java) }.map { it.name }.toSet()
        for (function in MoneroWallet::class.java.methods) {
            val prefix = "test" + function.name.replaceFirstChar { it.uppercase() }
            assertTrue(tested.any { it == prefix || it.startsWith(prefix + "With") }, "MoneroWallet.${function.name} has no test")
        }
        assertEquals(MoneroWallet::class.java.methods.size, tested.size, "The tests and the functions of the interface are not the same")
    }

    @NotSupported
    @Test
    fun testAddListener() {
        wallet.addListener(MoneroWalletListener())
    }

    @NotSupported
    @Test
    fun testRemoveListener() {
        wallet.removeListener(MoneroWalletListener())
    }

    @NotSupported
    @Test
    fun testGetListeners() {
        wallet.getListeners()
    }

    @NotSupported
    @Test
    fun testRemoveListeners() {
        wallet.removeListeners()
    }

    @NotSupported
    @Test
    fun testGetVersion() {
        wallet.getVersion()
    }

    @NotSupported
    @Test
    fun testGetPath() {
        wallet.getPath()
    }

    @NotSupported
    @Test
    fun testGetNetworkType() {
        wallet.getNetworkType()
    }

    @NotSupported
    @Test
    fun testIsViewOnly() {
        wallet.isViewOnly()
    }

    @NotSupported
    @Test
    fun testIsClosed() {
        wallet.isClosed()
    }

    @NotSupported
    @Test
    fun testGetSeed() {
        wallet.getSeed()
    }

    @NotSupported
    @Test
    fun testGetSeedLanguage() {
        wallet.getSeedLanguage()
    }

    @NotSupported
    @Test
    fun testGetPrivateViewKey() {
        wallet.getPrivateViewKey()
    }

    @NotSupported
    @Test
    fun testGetPrivateSpendKey() {
        wallet.getPrivateSpendKey()
    }

    @NotSupported
    @Test
    fun testGetPublicViewKey() {
        wallet.getPublicViewKey()
    }

    @NotSupported
    @Test
    fun testGetPublicSpendKey() {
        wallet.getPublicSpendKey()
    }

    @NotSupported
    @Test
    fun testGetPrimaryAddress() {
        wallet.getPrimaryAddress()
    }

    @NotSupported
    @Test
    fun testGetAddress() {
        wallet.getAddress(0, 0)
    }

    @NotSupported
    @Test
    fun testGetAddressIndex() {
        wallet.getAddressIndex("")
    }

    @NotSupported
    @Test
    fun testGetIntegratedAddress() {
        wallet.getIntegratedAddress()
    }

    @NotSupported
    @Test
    fun testDecodeIntegratedAddress() {
        wallet.decodeIntegratedAddress("")
    }

    @NotSupported
    @Test
    fun testGetAttribute() {
        wallet.getAttribute("")
    }

    @NotSupported
    @Test
    fun testSetAttribute() {
        wallet.setAttribute("", "")
    }

    @NotSupported
    @Test
    fun testGetDefaultFeePriority() {
        wallet.getDefaultFeePriority()
    }

    @NotSupported
    @Test
    fun testSave() {
        wallet.save()
    }

    @NotSupported
    @Test
    fun testMoveTo() {
        wallet.moveTo("", "")
    }

    @NotSupported
    @Test
    fun testChangePassword() {
        wallet.changePassword("", "")
    }

    @NotSupported
    @Test
    fun testClose() {
        wallet.close(false)
    }

    @NotSupported
    @Test
    fun testGetDaemonConnection() {
        wallet.getDaemonConnection()
    }

    @NotSupported
    @Test
    fun testSetDaemonConnectionWithConnection() {
        wallet.setDaemonConnection(null)
    }

    @NotSupported
    @Test
    fun testSetDaemonConnectionWithUri() {
        wallet.setDaemonConnection("")
    }

    @NotSupported
    @Test
    fun testIsConnectedToDaemon() {
        wallet.isConnectedToDaemon()
    }

    @NotSupported
    @Test
    fun testIsDaemonSynced() {
        wallet.isDaemonSynced()
    }

    @NotSupported
    @Test
    fun testIsDaemonTrusted() {
        wallet.isDaemonTrusted()
    }

    @NotSupported
    @Test
    fun testIsSynced() {
        wallet.isSynced()
    }

    @NotSupported
    @Test
    fun testGetHeight() {
        wallet.getHeight()
    }

    @NotSupported
    @Test
    fun testGetDaemonHeight() {
        wallet.getDaemonHeight()
    }

    @NotSupported
    @Test
    fun testGetDaemonMaxPeerHeight() {
        wallet.getDaemonMaxPeerHeight()
    }

    @NotSupported
    @Test
    fun testGetHeightByDate() {
        wallet.getHeightByDate(0, 0, 0)
    }

    @NotSupported
    @Test
    fun testGetRestoreHeight() {
        wallet.getRestoreHeight()
    }

    @NotSupported
    @Test
    fun testSetRestoreHeight() {
        wallet.setRestoreHeight(0L)
    }

    @NotSupported
    @Test
    fun testSyncWithoutArguments() {
        wallet.sync()
    }

    @NotSupported
    @Test
    fun testSyncWithListener() {
        wallet.sync(MoneroWalletListener())
    }

    @NotSupported
    @Test
    fun testStartSyncing() {
        wallet.startSyncing()
    }

    @NotSupported
    @Test
    fun testStopSyncing() {
        wallet.stopSyncing()
    }

    @NotSupported
    @Test
    fun testWaitForNextBlock() {
        wallet.waitForNextBlock()
    }

    @NotSupported
    @Test
    fun testScanTxs() {
        wallet.scanTxs(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testRescanSpent() {
        wallet.rescanSpent()
    }

    @NotSupported
    @Test
    fun testRescanBlockchain() {
        wallet.rescanBlockchain()
    }

    @NotSupported
    @Test
    fun testStartMining() {
        wallet.startMining()
    }

    @NotSupported
    @Test
    fun testStopMining() {
        wallet.stopMining()
    }

    @NotSupported
    @Test
    fun testGetBalance() {
        wallet.getBalance()
    }

    @NotSupported
    @Test
    fun testGetUnlockedBalance() {
        wallet.getUnlockedBalance()
    }

    @NotSupported
    @Test
    fun testGetAccounts() {
        wallet.getAccounts()
    }

    @NotSupported
    @Test
    fun testGetAccount() {
        wallet.getAccount(0)
    }

    @NotSupported
    @Test
    fun testCreateAccount() {
        wallet.createAccount()
    }

    @NotSupported
    @Test
    fun testGetAccountTags() {
        wallet.getAccountTags()
    }

    @NotSupported
    @Test
    fun testTagAccounts() {
        wallet.tagAccounts("", emptyList<Int>())
    }

    @NotSupported
    @Test
    fun testUntagAccounts() {
        wallet.untagAccounts(emptyList<Int>())
    }

    @NotSupported
    @Test
    fun testSetAccountTagLabel() {
        wallet.setAccountTagLabel("", "")
    }

    @NotSupported
    @Test
    fun testGetSubaddresses() {
        wallet.getSubaddresses(0)
    }

    @NotSupported
    @Test
    fun testGetSubaddress() {
        wallet.getSubaddress(0, 0)
    }

    @NotSupported
    @Test
    fun testCreateSubaddress() {
        wallet.createSubaddress(0)
    }

    @NotSupported
    @Test
    fun testSetSubaddressLabel() {
        wallet.setSubaddressLabel(0, 0)
    }

    @NotSupported
    @Test
    fun testGetTxsWithoutArguments() {
        wallet.getTxs()
    }

    @NotSupported
    @Test
    fun testGetTxsWithTxHashes() {
        wallet.getTxs(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testGetTx() {
        wallet.getTx("")
    }

    @NotSupported
    @Test
    fun testGetTransfersWithoutArguments() {
        wallet.getTransfers()
    }

    @NotSupported
    @Test
    fun testGetTransfersWithAccountIdx() {
        wallet.getTransfers(0)
    }

    @NotSupported
    @Test
    fun testGetOutputs() {
        wallet.getOutputs()
    }

    @NotSupported
    @Test
    fun testExportOutputs() {
        wallet.exportOutputs()
    }

    @NotSupported
    @Test
    fun testImportOutputs() {
        wallet.importOutputs("")
    }

    @NotSupported
    @Test
    fun testExportKeyImages() {
        wallet.exportKeyImages()
    }

    @NotSupported
    @Test
    fun testImportKeyImages() {
        wallet.importKeyImages(emptyList<MoneroKeyImage>())
    }

    @NotSupported
    @Test
    fun testFreezeOutput() {
        wallet.freezeOutput("")
    }

    @NotSupported
    @Test
    fun testThawOutput() {
        wallet.thawOutput("")
    }

    @NotSupported
    @Test
    fun testIsOutputFrozen() {
        wallet.isOutputFrozen("")
    }

    @NotSupported
    @Test
    fun testCreateTx() {
        wallet.createTx(MoneroTxConfig())
    }

    @NotSupported
    @Test
    fun testCreateTxs() {
        wallet.createTxs(MoneroTxConfig())
    }

    @NotSupported
    @Test
    fun testSweepOutput() {
        wallet.sweepOutput(MoneroTxConfig())
    }

    @NotSupported
    @Test
    fun testSweepUnlocked() {
        wallet.sweepUnlocked(MoneroTxConfig())
    }

    @NotSupported
    @Test
    fun testSweepDust() {
        wallet.sweepDust()
    }

    @NotSupported
    @Test
    fun testRelayTxWithTxMetadata() {
        wallet.relayTx("")
    }

    @NotSupported
    @Test
    fun testRelayTxWithTx() {
        wallet.relayTx(MoneroTxWallet())
    }

    @NotSupported
    @Test
    fun testRelayTxsWithTxMetadatas() {
        wallet.relayTxs(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testRelayTxsWithTxs() {
        wallet.relayTxs(emptyList<MoneroTxWallet>())
    }

    @NotSupported
    @Test
    fun testDescribeTxSet() {
        wallet.describeTxSet(MoneroTxSet())
    }

    @NotSupported
    @Test
    fun testDescribeUnsignedTxSet() {
        wallet.describeUnsignedTxSet("")
    }

    @NotSupported
    @Test
    fun testDescribeMultisigTxSet() {
        wallet.describeMultisigTxSet("")
    }

    @NotSupported
    @Test
    fun testSignTxs() {
        wallet.signTxs("")
    }

    @NotSupported
    @Test
    fun testSubmitTxs() {
        wallet.submitTxs("")
    }

    @NotSupported
    @Test
    fun testGetTxNote() {
        wallet.getTxNote("")
    }

    @NotSupported
    @Test
    fun testGetTxNotes() {
        wallet.getTxNotes(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testSetTxNote() {
        wallet.setTxNote("", "")
    }

    @NotSupported
    @Test
    fun testSetTxNotes() {
        wallet.setTxNotes(emptyList<String>(), emptyList<String>())
    }

    @NotSupported
    @Test
    fun testGetAddressBookEntries() {
        wallet.getAddressBookEntries()
    }

    @NotSupported
    @Test
    fun testAddAddressBookEntry() {
        wallet.addAddressBookEntry("")
    }

    @NotSupported
    @Test
    fun testEditAddressBookEntry() {
        wallet.editAddressBookEntry(0L, null, null)
    }

    @NotSupported
    @Test
    fun testDeleteAddressBookEntry() {
        wallet.deleteAddressBookEntry(0L)
    }

    @NotSupported
    @Test
    fun testGetPaymentUri() {
        wallet.getPaymentUri(MoneroTxConfig())
    }

    @NotSupported
    @Test
    fun testParsePaymentUri() {
        wallet.parsePaymentUri("")
    }

    @NotSupported
    @Test
    fun testSignMessage() {
        wallet.signMessage("")
    }

    @NotSupported
    @Test
    fun testVerifyMessage() {
        wallet.verifyMessage("", "", "")
    }

    @NotSupported
    @Test
    fun testGetTxKey() {
        wallet.getTxKey("")
    }

    @NotSupported
    @Test
    fun testCheckTxKey() {
        wallet.checkTxKey("", "", "")
    }

    @NotSupported
    @Test
    fun testGetTxProof() {
        wallet.getTxProof("", "")
    }

    @NotSupported
    @Test
    fun testCheckTxProof() {
        wallet.checkTxProof("", "", null, "")
    }

    @NotSupported
    @Test
    fun testGetSpendProof() {
        wallet.getSpendProof("")
    }

    @NotSupported
    @Test
    fun testCheckSpendProof() {
        wallet.checkSpendProof("", null, "")
    }

    @NotSupported
    @Test
    fun testGetReserveProofWallet() {
        wallet.getReserveProofWallet()
    }

    @NotSupported
    @Test
    fun testGetReserveProofAccount() {
        wallet.getReserveProofAccount(0, BigInteger.ZERO)
    }

    @NotSupported
    @Test
    fun testCheckReserveProof() {
        wallet.checkReserveProof("", null, "")
    }

    @NotSupported
    @Test
    fun testIsMultisig() {
        wallet.isMultisig()
    }

    @NotSupported
    @Test
    fun testGetMultisigInfo() {
        wallet.getMultisigInfo()
    }

    @NotSupported
    @Test
    fun testIsMultisigImportNeeded() {
        wallet.isMultisigImportNeeded()
    }

    @NotSupported
    @Test
    fun testPrepareMultisig() {
        wallet.prepareMultisig()
    }

    @NotSupported
    @Test
    fun testMakeMultisig() {
        wallet.makeMultisig(emptyList<String>(), 0, "")
    }

    @NotSupported
    @Test
    fun testExchangeMultisigKeys() {
        wallet.exchangeMultisigKeys(emptyList<String>(), "")
    }

    @NotSupported
    @Test
    fun testExportMultisigHex() {
        wallet.exportMultisigHex()
    }

    @NotSupported
    @Test
    fun testImportMultisigHex() {
        wallet.importMultisigHex(emptyList<String>())
    }

    @NotSupported
    @Test
    fun testSignMultisigTxHex() {
        wallet.signMultisigTxHex("")
    }

    @NotSupported
    @Test
    fun testSubmitMultisigTxHex() {
        wallet.submitMultisigTxHex("")
    }
}
