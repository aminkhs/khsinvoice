package ir.hesab.app

import android.Manifest
import android.content.ContentValues
import android.net.Uri
import android.provider.Telephony
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import ir.hesab.core.Kind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** The whole owner flow on a real inbox: find bank senders, trust them, read old messages, inspect the rest. */
class SmsSetupTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrument get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrument.targetContext.applicationContext as HesabApplication
    private val inserted=mutableListOf<Uri>()
    private var started=0L
    private var originalSenders=emptyMap<String,String>()
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8) }
    // Every number below is made up; only the layouts are the ones banks use.
    private val fixtures=listOf(
        "Bank Mellat" to "حساب1234567890\nبرداشت1,500,000\nمانده25,340,000\n040712-13:45",
        "Bank Mellat" to "حساب1234567890\nواریز2,000,000\nمانده27,340,000\n040713-09:10",
        "Bank Mellat" to "رمز پویا 123456 مبلغ 1,000,000 ریال",
        "+98700717" to "بانك ملي ايران\nخريد:250,000-\nحساب:67006\nمانده:5,123,456\n0712-13:45",
        "+98700717" to "انتقال:1,000,000-\nحساب:67006\nمانده:4,123,456\n0712-18:02",
        "+98700717" to "برداشت از حساب شما انجام شد",
        "+989999999999" to "سلام، فردا میای؟",
        "+989999999999" to "باشه ممنون"
    )
    @Before fun prepare() = runBlocking {
        instrument.uiAutomation.grantRuntimePermission(app.packageName,Manifest.permission.READ_SMS)
        instrument.uiAutomation.grantRuntimePermission(app.packageName,Manifest.permission.RECEIVE_SMS)
        originalSenders=app.preferences.flow.first().senders
        originalSenders.keys.forEach { app.preferences.removeSender(it) }
        app.preferences.set("onboarded",true); app.preferences.set("sms",false); app.preferences.set("lock",false)
        shell("appops set ${app.packageName} WRITE_SMS allow")
        started=System.currentTimeMillis()
        fixtures.forEachIndexed { index,(address,body) ->
            // The operator time differs from the arrival time, as it does on a phone.
            val values=ContentValues().apply { put("address",address); put("body",body); put("date",started+index*1000L); put("date_sent",started+index*1000L-4000L); put("read",1) }
            app.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI,values)?.let(inserted::add)
        }
        assertEquals("Synthetic inbox fixtures were not inserted",fixtures.size,inserted.size)
    }
    @Test fun findsBankSendersReadsHistoryAndShowsWhatWasNotRead() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("تنظیمات").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("تنظیمات").performClick()
        compose.onNodeWithText("پیدا کردن خودکار بانک‌ها از پیامک‌ها").performScrollTo().performClick()
        compose.waitUntil(20000) { compose.onAllNodesWithText("فرستنده‌های بانکی پیدا شده").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Bank Mellat").assertExists()
        compose.onNodeWithText("+98700717").assertExists()
        compose.onNodeWithText("+989999999999").assertDoesNotExist()
        compose.onNode(hasText("بانک: بانک ملت") and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasText("بانک: بانک ملی") and hasAnyAncestor(isDialog())).assertExists()
        compose.onNodeWithText("افزودن و خواندن پیامک‌ها").performClick()
        // The device inbox may hold older messages of the same senders; only the fixtures carry these times.
        fun imported() = runBlocking { app.db.dao().transactions().filter { it.source=="SMS" && it.createdAt>=started && it.timestamp>=started-10_000L } }
        compose.waitUntil(30000) { imported().size>=4 }
        compose.waitUntil(15000) { compose.onAllNodesWithText("باشه").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("باشه").performClick()
        val rows=imported()
        assertEquals(setOf(1500000L,2000000L,250000L,1000000L),rows.map { it.amountIrr }.toSet())
        assertEquals(Kind.INCOME,rows.single { it.amountIrr==2000000L }.kind)
        assertEquals(3,rows.count { it.kind==Kind.EXPENSE })
        // Each bank ends up with an account without the owner typing one (the device may already hold one).
        val accounts=runBlocking { app.db.dao().accounts() }
        assertTrue(rows.all { row -> accounts.any { it.id==row.accountId && it.bankId==row.bankId } })
        assertEquals(setOf("mellat","melli"),rows.map { it.bankId }.toSet())
        val prefs=runBlocking { app.preferences.flow.first() }
        assertTrue(prefs.smsEnabled); assertEquals("melli",prefs.bankFor("98700717")); assertEquals("mellat",prefs.bankFor("bank mellat"))
        compose.onNodeWithText("پیامک‌های بانکی که ثبت نشده‌اند").performScrollTo().performClick()
        compose.waitUntil(20000) { compose.onAllNodesWithText("پیامک‌های ثبت‌نشده").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("برداشت از حساب شما انجام شد").assertExists()
        // One-time passwords are never listed.
        compose.onAllNodesWithText("رمز پویا",substring=true).assertCountEquals(0)
    }
    @After fun clean() = runBlocking {
        inserted.forEach { runCatching { app.contentResolver.delete(it,null,null) } }
        shell("appops set ${app.packageName} WRITE_SMS default")
        val sql=app.db.openHelper.writableDatabase
        sql.execSQL("DELETE FROM transactions WHERE source='SMS' AND createdAt>=?",arrayOf<Any>(started))
        sql.execSQL("DELETE FROM sms_records WHERE timestamp>=?",arrayOf<Any>(started-10000L))
        sql.execSQL("DELETE FROM accounts WHERE bankId IN ('mellat','melli') AND id NOT IN (SELECT accountId FROM transactions WHERE accountId IS NOT NULL)")
        app.preferences.removeSender("Bank Mellat"); app.preferences.removeSender("+98700717")
        originalSenders.forEach { (name,bank) -> app.preferences.sender(name,bank) }
        app.preferences.set("onboarded",false); app.preferences.set("sms",false)
    }
}
