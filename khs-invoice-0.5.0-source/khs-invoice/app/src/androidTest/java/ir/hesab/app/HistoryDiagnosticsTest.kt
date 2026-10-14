package ir.hesab.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import android.provider.Telephony
import java.io.FileInputStream
import ir.hesab.core.Fingerprint
import ir.hesab.app.sms.HistoryWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*

class HistoryDiagnosticsTest {
    @Test fun historyMatchesSpacedSenderAndReimportDoesNotDuplicate() = runBlocking {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val app=instrument.targetContext.applicationContext as HesabApplication
        val original=app.preferences.flow.first()
        val address="0999\u00a0992\u00a00000"
        val body="خرید1000ریال"
        val time=System.currentTimeMillis()
        var rowId: Long?=null
        var originalShellSmsMode: String?=null
        fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8) }
        suspend fun runImport(): WorkInfo {
            val manager=WorkManager.getInstance(app)
            val request=OneTimeWorkRequestBuilder<HistoryWorker>().build()
            manager.enqueue(request)
            return withTimeout(20000) { manager.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished==true }!! }
        }
        try {
            instrument.uiAutomation.grantRuntimePermission(app.packageName,Manifest.permission.READ_SMS)
            original.senders.keys.forEach { app.preferences.removeSender(it) }
            app.preferences.set("sms",true)
            app.preferences.sender("۰۹۹۹ ۹۹۲ ۰۰۰۰","saman")
            originalShellSmsMode=Regex("WRITE_SMS: (allow|ignore|deny|default)").find(shell("appops get com.android.shell WRITE_SMS"))?.groupValues?.get(1) ?: "default"
            shell("appops set com.android.shell WRITE_SMS allow")
            // UiAutomation executes argv directly, without a shell to remove quotes.
            val fixtureResult=shell("content insert --uri content://sms/inbox --bind address:s:$address --bind body:s:$body --bind date:l:$time")
            app.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,arrayOf("_id"),"address=? AND date=?",arrayOf(address,time.toString()),null)!!.use { cursor ->
                assertTrue("Synthetic SMS fixture was not inserted: $fixtureResult",cursor.moveToFirst())
                rowId=cursor.getLong(0)
            }
            val first=runImport()
            assertEquals(WorkInfo.State.SUCCEEDED,first.state)
            assertTrue(first.outputData.getInt("matched",0)>=1)
            assertTrue(first.outputData.getInt("count",0)>=1)
            val key=Fingerprint.of(address,body,time)
            val transaction=app.db.dao().transactions().single { it.fingerprint==key }
            assertEquals("saman",transaction.bankId)
            val again=runImport()
            assertEquals(WorkInfo.State.SUCCEEDED,again.state)
            assertEquals(0,again.outputData.getInt("count",-1))
            assertTrue(again.outputData.getInt("duplicate",0)>=1)
            assertEquals(1,app.db.dao().transactions().count { it.fingerprint==key })
        } finally {
            rowId?.let { shell("content delete --uri content://sms/$it") }
            originalShellSmsMode?.let { shell("appops set com.android.shell WRITE_SMS $it") }
            app.preferences.removeSender("09999920000")
            original.senders.forEach { (name,bank) -> app.preferences.sender(name,bank) }
            app.preferences.set("sms",original.smsEnabled)
        }
    }
    @Test fun deniedPermissionReportsReasonAndUnknownSenderReportsZeroMatches() = runBlocking {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val app=instrument.targetContext.applicationContext as HesabApplication
        val manager=WorkManager.getInstance(app)
        suspend fun runImport(): WorkInfo {
            val request=OneTimeWorkRequestBuilder<HistoryWorker>().build()
            manager.enqueue(request)
            return withTimeout(20000) { manager.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished==true }!! }
        }
        val original=app.preferences.flow.first()
        val sender="HESAB-NO-SMS-${System.currentTimeMillis()}"
        try {
            // Revoking a granted permission kills the instrumented process. Exercise
            // denial on a fresh install, and keep repeated runs safe for the runner.
            if(ContextCompat.checkSelfPermission(app,Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED) {
                val denied=runImport()
                assertEquals(WorkInfo.State.FAILED,denied.state)
                assertTrue(denied.outputData.getString("error")!!.contains("مجوز"))
            }
            instrument.uiAutomation.grantRuntimePermission(app.packageName,Manifest.permission.READ_SMS)
            original.senders.keys.forEach { app.preferences.removeSender(it) }
            app.preferences.set("sms",true)
            app.preferences.sender(sender,"khavarmianeh")
            val empty=runImport()
            assertEquals(WorkInfo.State.SUCCEEDED,empty.state)
            assertEquals(0,empty.outputData.getInt("matched",-1))
            assertEquals(0,empty.outputData.getInt("count",-1))
        } finally {
            app.preferences.removeSender(sender)
            original.senders.forEach { (name,bank) -> app.preferences.sender(name,bank) }
            app.preferences.set("sms",original.smsEnabled)
        }
    }
}
