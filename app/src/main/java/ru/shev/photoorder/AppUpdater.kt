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
            setUseCaches(false)
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "FotoPoryadok-Android")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("GitHub ответил: ${connection.responseCode}")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
            val tag = release.optString("tag_name").trim()
            if (tag.isBlank()) error("GitHub вернул релиз без номера версии")
            val versionName = tag.removePrefix("v")
            val versionCode = Regex("""(?im)^\s*versionCode\s*:\s*(\d+)\s*$""")
                .find(release.optString("body"))?.groupValues?.get(1)?.toIntOrNull()
                ?: error("В релизе $tag отсутствует корректная строка versionCode")
            val assets = release.optJSONArray("assets")
                ?: error("В релизе $tag не найден список файлов APK")
            var downloadUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name") == APK_NAME) {
                    downloadUrl = asset.optString("browser_download_url")
                    break
                }
            }
            if (downloadUrl.isNullOrBlank()) {
                error("В релизе $tag отсутствует файл $APK_NAME")
            }
            if (versionCode <= installedVersionCode) {
                null
            } else {
                UpdateInfo(versionCode, versionName, downloadUrl, release.optString("body"))
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        update: UpdateInfo,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
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
            val directory = File(context.cacheDir, "updates").apply {
                if (!exists() && !mkdirs()) error("Не удалось создать папку для обновления")
            }
            val apk = File(directory, APK_NAME)
            val partial = File(directory, "$APK_NAME.part")
            partial.delete()

            val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30000
                readTimeout = 90000
                setRequestProperty("User-Agent", "FotoPoryadok-Android")
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                val response = connection.responseCode
                if (response !in 200..299) {
                    error("Сервер загрузки ответил: HTTP $response")
                }
                val total = connection.contentLengthLong
                if (total == 0L) error("Сервер вернул пустой файл обновления")

                var downloaded = 0L
                var lastReported = 0L
                connection.inputStream.buffered().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (downloaded - lastReported >= 256 * 1024) {
                                lastReported = downloaded
                                withContext(Dispatchers.Main) { onProgress(downloaded, total) }
                            }
                        }
                    }
                }
                if (downloaded < 2L) error("Скачанный файл слишком маленький")
                if (total > 0L && downloaded != total) {
                    error("Загрузка оборвалась: получено $downloaded из $total байт")
                }
                val signature = partial.inputStream().use { input ->
                    byteArrayOf(input.read().toByte(), input.read().toByte())
                }
                if (signature[0] != 0x50.toByte() || signature[1] != 0x4B.toByte()) {
                    error("Сервер вернул не APK-файл. Проверь соединение и повтори попытку")
                }
                if (apk.exists() && !apk.delete()) error("Не удалось заменить старый файл обновления")
                if (!partial.renameTo(apk)) error("Не удалось сохранить скачанный APK")
                withContext(Dispatchers.Main) { onProgress(downloaded, if (total > 0) total else downloaded) }
            } catch (error: Exception) {
                partial.delete()
                throw error
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
