package ir.hesab.app.sms

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import ir.hesab.app.MainActivity
import ir.hesab.app.R
import ir.hesab.app.data.*
import ir.hesab.core.*

class Notifications(private val context: Context) {
    private val manager=context.getSystemService(NotificationManager::class.java)
    fun channels() {
        manager.createNotificationChannel(NotificationChannel("categorization","دسته‌بندی تراکنش",NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel("budgets","هشدار بودجه",NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun allowed() = Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    fun cancel(id: String) = manager.cancel(id.hashCode())
    fun expense(t: Transaction,prefs: UserPreferences) {
        if(!allowed()) return
        val open=Intent(context,MainActivity::class.java).putExtra("transaction",t.id).setData("hesab://transaction/${t.id}".toUri())
        val pending=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=NotificationCompat.Builder(context,"categorization").setSmallIcon(R.drawable.ic_notification).setContentTitle("این هزینه برای چی بود؟").setContentText(if(prefs.hideDetails) "یک تراکنش جدید برای دسته‌بندی" else "${Money.format(t.amountIrr,prefs.toman)} از ${Banks.all.find { it.id==t.bankId }?.title ?: "حساب"}").setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        // Android notifications reliably show up to three actions; the editor exposes every category.
        listOf("cafe" to "☕ کافه","food" to "🍔 رستوران","shopping" to "🛒 خرید").forEach { (id,title) ->
            val intent=Intent(context,CategoryReceiver::class.java).putExtra("transaction",t.id).putExtra("category",id).setData("hesab://categorize/${t.id}/$id".toUri())
            builder.addAction(0,title,PendingIntent.getBroadcast(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        builder.setPublicVersion(NotificationCompat.Builder(context,"categorization").setSmallIcon(R.drawable.ic_notification).setContentTitle(context.getString(R.string.app_name)).setContentText("تراکنش جدید").build())
        manager.notify(t.id.hashCode(),builder.build())
    }
    fun budget(id: String,threshold: Int) {
        if(allowed()) manager.notify(("budget$id$threshold").hashCode(),NotificationCompat.Builder(context,"budgets").setSmallIcon(R.drawable.ic_notification).setContentTitle("بودجه ماهانه").setContentText("${Money.persian(threshold.toString())}٪ بودجه مصرف شده").setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
    }
}
