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
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
                    // Leave the version unmarked so checking resumes after the user enables this channel.
                    return Result.success()
                }
            }

            val posted = showUpdateNotification(update.versionName)
            if (posted) {
                prefs.edit().putInt(KEY_NOTIFIED_VERSION, update.versionCode).apply()
                Result.success()
            } else {
                // Do not remember the version if Android rejected the notification.
                Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showUpdateNotification(versionName: String): Boolean {
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
        return try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            // Notification permission may be revoked between the permission check and posting.
            false
        }
    }

    companion object {
        private const val PREFS = "photoorder_update_worker"
        private const val KEY_NOTIFIED_VERSION = "notified_version_code"
        private const val CHANNEL_ID = "photoorder_updates"
        private const val NOTIFICATION_ID = 1002
        private const val UNIQUE_WORK = "photoorder_periodic_update_check"
        private const val IMMEDIATE_WORK = "photoorder_update_check_immediate"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val manager = WorkManager.getInstance(context)

            // Check soon after app startup, then keep checking in the background.
            manager.enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                    .setConstraints(constraints)
                    .build()
            )

            // Android's minimum periodic interval is 15 minutes. UPDATE replaces
            // an older 12-hour schedule already stored on the device.
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            manager.enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
