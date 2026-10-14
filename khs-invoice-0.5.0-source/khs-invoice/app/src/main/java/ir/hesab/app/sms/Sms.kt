package ir.hesab.app.sms

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.work.*
import ir.hesab.app.HesabApplication
import ir.hesab.core.*
import ir.hesab.app.data.ImportBatch
import ir.hesab.app.data.Ingest
import ir.hesab.app.data.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BankSmsReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action!=Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pending=goAsync()
        val app=context.applicationContext as HesabApplication
        app.scope.launchSafely(pending) {
            val prefs=app.preferences.flow.first()
            if(!prefs.smsEnabled) return@launchSafely
            val parts=Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if(parts.isNullOrEmpty()) return@launchSafely
            val sender=parts.first().originatingAddress ?: return@launchSafely
            val bank=prefs.bankFor(sender) ?: return@launchSafely
            if(parts.any { it.originatingAddress!=sender }) return@launchSafely
            val body=parts.joinToString("") { it.messageBody ?: "" }
            val time=parts.first().timestampMillis
            when(val parsed=BankParser().parse(body,bank)) {
                is ParseResult.Ignored -> Unit
                is ParseResult.Review -> {
                    // Store only the non-sensitive outcome, never the message body.
                    app.db.dao().save(ir.hesab.app.data.SMSProcessingRecord(Fingerprint.of(sender,body,time),time,"REVIEW:${parsed.reason}",null))
                }
                is ParseResult.Transaction -> {
                    // Encryption keeps the temporary durable queue private. OTP is rejected before this point.
                    val key=Fingerprint.of(sender,body,time)
                    val file=java.io.File(context.noBackupFilesDir,"sms-$key.queue")
                    ir.hesab.app.security.QueueCipher(context).write(file,"$sender\n$time\n$body")
                    WorkManager.getInstance(context).enqueueUniqueWork("sms-$key",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<SmsWorker>().setInputData(workDataOf("queue" to file.name)).build())
                }
            }
        }
    }
}
private fun kotlinx.coroutines.CoroutineScope.launchSafely(pending: BroadcastReceiver.PendingResult,block: suspend () -> Unit) = this.launch {
    // A failure here must not take the whole app down with the receiver.
    try { block() } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch(_: Exception) { } finally { pending.finish() }
}
class SmsWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val name=inputData.getString("queue") ?: return Result.failure()
        if(!name.matches(Regex("sms-[a-f0-9]{64}\\.queue"))) return Result.failure()
        val file=java.io.File(applicationContext.noBackupFilesDir,name)
        if(!file.exists()) return Result.success()
        return try {
            val content=ir.hesab.app.security.QueueCipher(applicationContext).read(file).split('\n',limit=3)
            val app=applicationContext as HesabApplication
            app.repository.ingest(content[0],content[2],content[1].toLong())
            file.delete(); Result.success()
        } catch(_: Exception) { if(runAttemptCount<3) Result.retry() else { file.delete(); Result.failure() } }
    }
}
data class SenderCandidate(val address: String,val bank: String,val total: Int,val transactions: Int,val likely: Boolean)
data class UnreadSms(val address: String,val time: Long,val body: String,val reason: String)
/** Read-only helpers over the system inbox. Nothing here is persisted. */
object SmsInbox {
    private const val WEEK=7*86_400_000L
    fun canRead(context: Context) = ContextCompat.checkSelfPermission(context,Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED
    fun canReceive(context: Context) = ContextCompat.checkSelfPermission(context,Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED
    /** The operator time is what the live receiver sees, so it is the preferred identity of a stored message. */
    fun eventTime(date: Long,sent: Long) = if(sent>0 && kotlin.math.abs(sent-date)<=WEEK) sent else date
    /** Some vendor inboxes have no date_sent column; fall back to the arrival time alone. The caller closes the cursor. */
    @android.annotation.SuppressLint("Recycle")
    fun query(context: Context,selection: String?,arguments: Array<String>?,order: String): Pair<android.database.Cursor?,Boolean> = try {
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,arrayOf("address","body","date","date_sent"),selection,arguments,order) to true
    } catch(denied: SecurityException) { throw denied } catch(_: Exception) {
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,arrayOf("address","body","date"),selection,arguments,order) to false
    }
    /** Groups the newest inbox messages by sender and keeps the senders whose messages read as account events. */
    fun detectSenders(context: Context,limit: Int=6000): List<SenderCandidate> {
        class Tally(val address: String) { var total=0; var tested=0; var parsed=0; var withBalance=0; val samples=mutableListOf<String>() }
        val tallies=linkedMapOf<String,Tally>()
        val parser=BankParser()
        val balance=Regex("مانده|موجودی")
        val (cursor,_)=query(context,null,null,"date DESC")
        cursor?.use {
            var rows=0
            while(it.moveToNext() && rows++<limit) {
                val address=it.getString(0) ?: continue
                val body=it.getString(1) ?: continue
                val tally=tallies.getOrPut(SmsSender.key(address)) { Tally(address) }
                tally.total++
                if(tally.tested>=40) continue
                tally.tested++
                if(parser.parse(body,"other") is ParseResult.Transaction) {
                    tally.parsed++
                    if(balance.containsMatchIn(Money.normalize(body))) tally.withBalance++
                    if(tally.samples.size<5) tally.samples.add(body)
                }
            }
        }
        return tallies.values.filter { it.parsed>=2 && it.parsed*5>=it.tested }.map { tally ->
            val bank=Banks.guess(tally.address,tally.samples)
            val personal=SmsSender.key(tally.address).matches(Regex("9[0-9]{9}"))
            SenderCandidate(tally.address,bank,tally.total,tally.parsed,!personal && (bank!="other" || tally.withBalance*2>=tally.parsed))
        }.sortedWith(compareByDescending<SenderCandidate> { it.likely }.thenByDescending { it.transactions })
    }
    /** Newest messages of trusted senders that did not become a transaction, with the reason. Shown on screen only. */
    fun unread(context: Context,prefs: UserPreferences,limit: Int=40): List<UnreadSms> {
        val result=mutableListOf<UnreadSms>()
        val parser=BankParser()
        val (cursor,_)=query(context,null,null,"date DESC")
        cursor?.use {
            var rows=0
            while(it.moveToNext() && rows++<4000 && result.size<limit) {
                val address=it.getString(0) ?: continue
                val bank=prefs.bankFor(address) ?: continue
                val body=it.getString(1) ?: continue
                val reason=when(val parsed=parser.parse(body,bank)) {
                    is ParseResult.Transaction -> continue
                    is ParseResult.Review -> parsed.reason
                    is ParseResult.Ignored -> if(parsed.reason=="unsupported") parsed.reason else continue
                }
                result.add(UnreadSms(address,it.getLong(2),body,reason))
            }
        }
        return result
    }
}
class HistoryWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    companion object {
        fun enqueue(context: Context) { WorkManager.getInstance(context).enqueueUniqueWork("history",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<HistoryWorker>().addTag("history-run-${System.currentTimeMillis()}").build()) }
    }
    override suspend fun doWork(): Result {
        if(!SmsInbox.canRead(applicationContext)) return Result.failure(workDataOf("error" to "مجوز خواندن پیامک در تنظیمات Android داده نشده است"))
        val app=applicationContext as HesabApplication
        val prefs=app.preferences.flow.first()
        if(!prefs.smsEnabled || prefs.senders.isEmpty()) return Result.failure(workDataOf("error" to "پردازش پیامک یا فرستنده‌های مجاز تنظیم نشده‌اند"))
        val batch=ImportBatch(startedAt=System.currentTimeMillis(),count=0,completedAt=null)
        app.db.dao().save(batch)
        var count=0
        var matched=0
        var review=0
        var ignored=0
        var duplicate=0
        var firstReason=""
        fun summary() = workDataOf("count" to count,"matched" to matched,"review" to review,"ignored" to ignored,"duplicate" to duplicate,"reason" to firstReason)
        val parser=BankParser()
        try {
        // Inspect sender metadata first. Only fetch bodies belonging to the trusted senders.
        val rawSenders=linkedSetOf<String>()
        val addresses=applicationContext.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,arrayOf("address"),null,null,null)
            ?: return Result.failure(workDataOf("error" to "صندوق پیامک در دسترس نیست"))
        addresses.use { cursor ->
            while(cursor.moveToNext()) {
                if(isStopped) return Result.retry()
                val address=cursor.getString(0) ?: continue
                if(prefs.bankFor(address)!=null) rawSenders.add(address)
            }
        }
        for(senders in rawSenders.chunked(400)) {
        val placeholders=senders.joinToString(",") { "?" }
        val (cursor,hasSent)=SmsInbox.query(applicationContext,"address IN ($placeholders)",senders.toTypedArray(),"date ASC")
        if(cursor==null) return Result.failure(workDataOf("error" to "صندوق پیامک در دسترس نیست"))
        cursor.use {
            while(cursor.moveToNext()) {
                if(isStopped) return Result.retry()
                val sender=cursor.getString(0) ?: continue
                val body=cursor.getString(1) ?: continue
                val bank=prefs.bankFor(sender) ?: continue
                matched++
                val date=cursor.getLong(2)
                val time=SmsInbox.eventTime(date,if(hasSent) cursor.getLong(3) else 0L)
                when(app.repository.ingest(sender,body,time,listOf(date),false)) {
                    Ingest.CREATED -> count++
                    Ingest.DUPLICATE -> duplicate++
                    Ingest.REVIEW -> { review++; if(firstReason.isEmpty()) firstReason=(parser.parse(body,bank) as? ParseResult.Review)?.reason ?: "" }
                    Ingest.IGNORED -> { ignored++; if(firstReason.isEmpty()) (parser.parse(body,bank) as? ParseResult.Ignored)?.reason?.takeIf { it=="unsupported" }?.let { firstReason=it } }
                }
                if(matched % 25==0) setProgress(summary())
            }
        }
        }
        // Budgets are checked once here instead of after every old message.
        app.repository.checkBudgets(System.currentTimeMillis())
        } catch(cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch(_: SecurityException) {
            return Result.failure(workDataOf("error" to "Android اجازه خواندن صندوق پیامک را نداد؛ مجوز SMS و محدودیت دسترسی برنامه را بررسی کنید"))
        } catch(_: Exception) {
            return Result.failure(workDataOf("error" to "وارد کردن تاریخچه کامل نشد؛ دوباره تلاش کنید"))
        }
        app.db.dao().save(batch.copy(count=count,completedAt=System.currentTimeMillis()))
        return Result.success(summary())
    }
}
class CategoryReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val id=intent.getStringExtra("transaction") ?: return
        val category=intent.getStringExtra("category") ?: return
        val pending=goAsync()
        val app=context.applicationContext as HesabApplication
        app.scope.launchSafely(pending) { app.repository.categorize(id,category) }
    }
}
