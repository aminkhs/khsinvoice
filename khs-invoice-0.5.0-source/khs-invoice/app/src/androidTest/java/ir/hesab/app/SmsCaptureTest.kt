package ir.hesab.app

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import ir.hesab.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*

/** Run with scripts/test-sms.ps1: a real emulator SMS arrives while this test waits. */
class SmsCaptureTest {
    @Test fun realSmsBecomesTransactionAndNotificationActionCategorizesIt() = runBlocking {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val app=instrument.targetContext.applicationContext as HesabApplication
        instrument.uiAutomation.grantRuntimePermission(app.packageName,Manifest.permission.RECEIVE_SMS)
        if(Build.VERSION.SDK_INT>=33) instrument.uiAutomation.grantRuntimePermission(app.packageName,Manifest.permission.POST_NOTIFICATIONS)
        app.preferences.set("sms",true); app.preferences.sender("5550100","mellat")
        app.db.dao().save(Bank("mellat","بانک ملت"))
        app.db.dao().save(FinancialAccount("sms-test-account","mellat","حساب مصنوعی آزمون","8765"))
        app.db.dao().save(Category("cafe","کافه","☕"))
        val started=System.currentTimeMillis()
        var transaction: Transaction? = null
        withTimeout(90000) {
            while(transaction==null) { transaction=app.db.dao().transactions().firstOrNull { it.createdAt>=started && it.source=="SMS" && it.accountId=="sms-test-account" }; if(transaction==null) delay(250) }
        }
        val saved=transaction!!
        assertEquals(123000L,saved.amountIrr)
        val manager=app.getSystemService(NotificationManager::class.java)
        var notification: android.service.notification.StatusBarNotification?=null
        withTimeout(10000) { while(notification==null) { notification=manager.activeNotifications.firstOrNull { it.id==saved.id.hashCode() }; if(notification==null) delay(100) } }
        assertFalse(notification!!.notification.extras.getString("android.text")!!.contains("123"))
        notification!!.notification.actions.first().actionIntent.send()
        withTimeout(10000) { while(app.db.dao().transaction(saved.id)?.categoryId!="cafe") delay(100) }
        assertEquals("cafe",app.db.dao().transaction(saved.id)!!.categoryId)
        // Reading the inbox afterwards must recognise the message that was already captured live,
        // although the inbox files it under its arrival time.
        instrument.uiAutomation.grantRuntimePermission(app.packageName,Manifest.permission.READ_SMS)
        val work=androidx.work.WorkManager.getInstance(app)
        val request=androidx.work.OneTimeWorkRequestBuilder<ir.hesab.app.sms.HistoryWorker>().build()
        work.enqueue(request)
        val info=withTimeout(60000) { work.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished==true }!! }
        assertEquals(androidx.work.WorkInfo.State.SUCCEEDED,info.state)
        assertTrue(info.outputData.getInt("duplicate",0)>=1)
        assertEquals(1,app.db.dao().transactions().count { it.source=="SMS" && it.amountIrr==123000L && kotlin.math.abs(it.timestamp-saved.timestamp)<120000L })
        app.preferences.set("sms",false)
    }
}
