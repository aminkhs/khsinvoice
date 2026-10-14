package ir.hesab.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** The promise in the README, checked on the installed app: it cannot go online. */
class NoInternetTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun installedAppRequestsNoNetworkPermission() {
        val requested=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        val network=setOf(Manifest.permission.INTERNET,Manifest.permission.ACCESS_NETWORK_STATE,Manifest.permission.ACCESS_WIFI_STATE,Manifest.permission.CHANGE_NETWORK_STATE,Manifest.permission.CHANGE_WIFI_STATE,Manifest.permission.BLUETOOTH,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.NFC)
        assertEquals("Network permissions found in the installed app",emptySet<String>(),requested intersect network)
        assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.INTERNET))
        // The exact list is pinned, so a new library permission cannot slip in unnoticed.
        val expected=setOf(Manifest.permission.RECEIVE_SMS,Manifest.permission.READ_SMS,Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.USE_BIOMETRIC,"android.permission.USE_FINGERPRINT",Manifest.permission.WAKE_LOCK,Manifest.permission.RECEIVE_BOOT_COMPLETED,Manifest.permission.FOREGROUND_SERVICE,"${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        assertEquals(expected,requested)
    }
    @Test fun systemRefusesASocketToTheApp() {
        // Without the INTERNET permission the app's process is not in the network group and cannot open a socket at all.
        val failure=runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress("10.0.2.2",80),2000) } }.exceptionOrNull()
        assertNotNull("A socket was opened",failure)
        val text=generateSequence(failure) { it.cause }.joinToString(" | ") { "${it.javaClass.simpleName}: ${it.message}" }
        assertTrue("Expected a permission refusal, got: $text",failure is SecurityException || text.contains("EPERM") || text.contains("EACCES"))
    }
}
