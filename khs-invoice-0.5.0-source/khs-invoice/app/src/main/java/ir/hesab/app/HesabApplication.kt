package ir.hesab.app

import android.app.Application
import ir.hesab.app.data.*
import ir.hesab.app.sms.Notifications
import kotlinx.coroutines.*
import ir.hesab.core.Banks

class HesabApplication: Application() {
    val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val db by lazy { HesabDatabase.open(this) }
    val preferences by lazy { Preferences(this) }
    val repository by lazy { Repository(db,preferences,Notifications(this)) }
    override fun onCreate() {
        super.onCreate()
        Notifications(this).channels()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("maintenance",androidx.work.ExistingPeriodicWorkPolicy.KEEP,androidx.work.PeriodicWorkRequestBuilder<ir.hesab.app.sms.MaintenanceWorker>(1,java.util.concurrent.TimeUnit.DAYS).build())
        scope.launch {
            Banks.all.forEach { db.dao().save(Bank(it.id,it.title)) }
            if(db.dao().categories().isEmpty()) listOf(
                Category("cafe","کافه","☕"),Category("food","رستوران","🍔"),Category("shopping","خرید","🛒"),Category("transport","حمل‌ونقل","🚕"),Category("home","خانه","🏠"),Category("fun","سرگرمی","🎮"),Category("work","کاری","💼"),Category("other","سایر","📝")
            ).forEach { db.dao().save(it) }
            // Installs from before deposit categories existed get them on the next start.
            val known=db.dao().categories().map { it.id }.toSet()
            depositCategories.filter { it.id !in known }.forEach { db.dao().save(it) }
            repository.ensureBudgets(System.currentTimeMillis())
        }
    }
}
