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
        saveStatus("Фоновая проверка выполняется…")
        return try {
            val update = AppUpdater.check(BuildConfig.VERSION_CODE) ?: run {
                saveStatus("Новых версий не найдено")
                return Result.success()
            }
            val appPrefs = applicationContext.getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE)
            if (!appPrefs.getBoolean("notifications_enabled", true)) {
                saveStatus("Уведомления выключены в настройках ФотоПорядка")
                return Result.success()
            }
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val notifiedVersion = prefs.getInt(KEY_NOTIFIED_VERSION, 0)
            if (update.versionCode <= notifiedVersion) {
                saveStatus("Для версии ${update.versionName} уведомление уже отправлялось")
                return Result.success()
            }

            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                saveStatus("Нет разрешения Android на отправку уведомлений")
                return Result.success()
            }
            if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                saveStatus("Уведомления приложения запрещены в настройках Android")
                return Result.success()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
                    saveStatus("Канал уведомлений ФотоПорядка выключен в Android")
                    return Result.success()
                }
            }

            val posted = showUpdateNotification(update.versionName)
            if (posted) {
                prefs.edit().putInt(KEY_NOTIFIED_VERSION, update.versionCode).apply()
                saveStatus("Найдено обновление ${update.versionName}; уведомление отправлено")
                Result.success()
            } else {
                saveStatus("Android не принял уведомление; будет повторная попытка")
                Result.retry()
            }
        } catch (error: Exception) {
            saveStatus("Ошибка фоновой проверки: ${error.message ?: error.javaClass.simpleName}")
            Result.retry()
        } finally {
            // Temporary test mode: append another one-time check after this run.
            // Periodic WorkManager jobs cannot run more often than every 15 minutes.
            if (FAST_TEST_MODE) enqueueNextTestCheck()
        }
    }

    private fun enqueueNextTestCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(constraints)
            .setInitialDelay(TEST_INTERVAL_MINUTES.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    private fun saveStatus(message: String) {
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_STATUS, message)
            .putLong(KEY_LAST_CHECKED_AT, System.currentTimeMillis())
            .apply()
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
        private const val KEY_LAST_STATUS = "last_check_status"
        private const val KEY_LAST_CHECKED_AT = "last_check_timestamp"
        private const val CHANNEL_ID = "photoorder_updates"
        private const val NOTIFICATION_ID = 1002
        private const val UNIQUE_WORK = "photoorder_periodic_update_check"
        private const val IMMEDIATE_WORK = "photoorder_update_check_immediate"

        // TEMPORARY: set FAST_TEST_MODE back to false after notification testing.
        internal const val FAST_TEST_MODE = true
        internal const val TEST_INTERVAL_MINUTES = 2

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val manager = WorkManager.getInstance(context)

            if (FAST_TEST_MODE) {
                // Replace the normal periodic schedule with a temporary 2-minute
                // one-time chain. Android may still defer execution in Doze/HyperOS.
                manager.cancelUniqueWork(UNIQUE_WORK)
                manager.enqueueUniqueWork(
                    IMMEDIATE_WORK,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                        .setConstraints(constraints)
                        .setInitialDelay(TEST_INTERVAL_MINUTES.toLong(), TimeUnit.MINUTES)
                        .build()
                )
            } else {
                // Normal production schedule: immediate check + 15-minute periodic work.
                manager.enqueueUniqueWork(
                    IMMEDIATE_WORK,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                        .setConstraints(constraints)
                        .build()
                )
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
}
