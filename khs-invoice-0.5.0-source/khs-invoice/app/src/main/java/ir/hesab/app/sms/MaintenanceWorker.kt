package ir.hesab.app.sms

import android.content.Context
import androidx.work.*
import ir.hesab.app.HesabApplication

class MaintenanceWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = try {
        val app=applicationContext as HesabApplication
        app.repository.checkBudgets(System.currentTimeMillis())
        // Recover encrypted queues if the process died between disk write and enqueue.
        applicationContext.noBackupFilesDir.listFiles()?.filter { it.name.matches(Regex("sms-[a-f0-9]{64}\\.queue")) }?.forEach { file -> WorkManager.getInstance(applicationContext).enqueueUniqueWork(file.name.removeSuffix(".queue"),ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<SmsWorker>().setInputData(workDataOf("queue" to file.name)).build()) }
        Result.success()
    } catch(_: Exception) { Result.retry() }
}
