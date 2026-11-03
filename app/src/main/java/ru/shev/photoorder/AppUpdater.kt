package ru.shev.photoorder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val notes: String
)

internal object AppUpdater {
    private const val RELEASE_API =
        "https://api.github.com/repos/shevgti/Fotoporyadok/releases/latest"
    private const val APK_NAME = "app-release.apk"

    suspend fun check(installedVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "FotoPoryadok-Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("GitHub ответил: ${connection.responseCode}")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
            val tag = release.optString("tag_name")
            val versionName = tag.removePrefix("v")
            val versionCode = Regex("""(?m)^versionCode:\\s*(\\d+)\\s*$""")
                .find(release.optString("body"))?.groupValues?.get(1)?.toIntOrNull()
                ?: 0
            val assets = release.optJSONArray("assets") ?: return@withContext null
            var downloadUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name") == APK_NAME) {
                    downloadUrl = asset.optString("browser_download_url")
                    break
                }
            }
            if (versionCode <= installedVersionCode || downloadUrl.isNullOrBlank()) {
                null
            } else {
                UpdateInfo(versionCode, versionName, downloadUrl, release.optString("body"))
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndInstall(context: Context, update: UpdateInfo): Boolean {
        val canInstall = withContext(Dispatchers.Main) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(settingsIntent)
                false
            } else true
        }
        if (!canInstall) return false

        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(directory, APK_NAME)
            val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20000
                readTimeout = 60000
                setRequestProperty("User-Agent", "FotoPoryadok-Android")
            }
            try {
                if (connection.responseCode !in 200..299) error("Не удалось скачать APK")
                connection.inputStream.use { input ->
                    apk.outputStream().use { output -> input.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }

            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", apk
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(installIntent)
            }
        }
        return true
    }
}
}
