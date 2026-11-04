package ru.shev.photoorder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val update = AppUpdater.check(BuildConfig.VERSION_CODE) ?: return Result.success()
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val notifiedVersion = prefs.getInt(KEY_NOTIFIED_VERSION, 0)
            if (update.versionCode <= notifiedVersion) return Result.success()

            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return Result.success()
            if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()

            showUpdateNotification(update.versionName)
            prefs.edit().putInt(KEY_NOTIFIED_VERSION, update.versionCode).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showUpdateNotification(versionName: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Уведомления ФотоПорядка", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pending = launchIntent?.let {
            PendingIntent.getActivity(
                applicationContext, NOTIFICATION_ID, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Доступно обновление ФотоПорядка")
            .setContentText("Новая версия $versionName готова к установке.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Новая версия $versionName готова к установке. Нажми, чтобы открыть ФотоПорядок."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { if (pending != null) setContentIntent(pending) }
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission may be revoked between the permission check and posting.
        }
    }

    companion object {
        private const val PREFS = "photoorder_update_worker"
        private const val KEY_NOTIFIED_VERSION = "notified_version_code"
        private const val CHANNEL_ID = "photoorder_updates"
        private const val NOTIFICATION_ID = 2201
        private const val UNIQUE_WORK = "photoorder_periodic_update_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
