package ru.shev.photoorder

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class MediaItem(
    val uri: Uri,
    val name: String,
    val dateSeconds: Long,
    val isVideo: Boolean
)

private enum class MediaSort { NEWEST, OLDEST, NAME }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9B5CFF),
                    onPrimary = Color.White,
                    secondary = Color(0xFF42C8FF),
                    background = Color(0xFF080B20),
                    surface = Color(0xFF151A35),
                    onSurface = Color(0xFFF7F4FF)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080B20)) {
                    PhotoOrderScreen()
                }
            }
        }
    }
}

@Composable
private fun PhotoOrderScreen() {
    val context = LocalContext.current
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(hasMediaPermission(context)) }
    var loading by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(MediaSort.NEWEST) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedPhoto by remember { mutableStateOf<MediaItem?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasMediaPermission(context)
    }

    LaunchedEffect(hasPermission, refreshKey) {
        if (hasPermission) {
            loading = true
            media = withContext(Dispatchers.IO) { loadMedia(context) }
            loading = false
        }
    }

    val tabMedia = when (selectedTab) {
        1 -> media.filter { !it.isVideo }
        2 -> media.filter { it.isVideo }
        else -> media
    }
    val visibleMedia = remember(tabMedia, search, sort) {
        val filtered = tabMedia.filter { it.name.contains(search.trim(), ignoreCase = true) }
        when (sort) {
            MediaSort.NEWEST -> filtered.sortedByDescending { it.dateSeconds }
            MediaSort.OLDEST -> filtered.sortedBy { it.dateSeconds }
            MediaSort.NAME -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080B20))
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFF5427D8))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) { Text("◉", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                Column {
                    Text("ФотоПорядок", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("ТВОЯ МЕДИАТЕКА", fontSize = 9.sp, color = Color(0xFF9D8CFF), fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    scope.launch {
                        checkingUpdate = true
                        updateMessage = null
                        try {
                            updateInfo = AppUpdater.check(BuildConfig.VERSION_CODE)
                            if (updateInfo == null) updateMessage = "У тебя уже установлена последняя версия."
                        } catch (error: Exception) {
                            updateMessage = "Не удалось проверить обновление: ${error.message ?: "ошибка сети"}"
                        } finally { checkingUpdate = false }
                    }
                }, enabled = !checkingUpdate && !downloadingUpdate) {
                    Text(if (checkingUpdate) "Проверка…" else "Обновить", fontSize = 13.sp, color = Color(0xFFB99AFF))
                }
                TextButton(onClick = { refreshKey++ }, enabled = hasPermission && !loading) {
                    Text("↻", fontSize = 22.sp, color = Color(0xFFB99AFF))
                }
            }
        }
        Text(
            "Фото и видео на телефоне — в удобном порядке",
            color = Color(0xFFAAA6C7),
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171B39))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Разрешите доступ к фото и видео", fontWeight = FontWeight.SemiBold)
                    Text("Приложение показывает медиафайлы, которые уже хранятся на телефоне. Оно ничего не загружает в интернет и не удаляет.")
                    Button(onClick = { permissionLauncher.launch(requiredMediaPermissions()) }) {
                        Text("Выбрать фото и видео")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                StatTile("ВСЕГО", media.size.toString(), Modifier.weight(1f), Color(0xFF5427D8))
                StatTile("ФОТО", media.count { !it.isVideo }.toString(), Modifier.weight(1f), Color(0xFF174E9D))
                StatTile("ВИДЕО", media.count { it.isVideo }.toString(), Modifier.weight(1f), Color(0xFF7B2DBB))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)
                    .clip(RoundedCornerShape(11.dp)).background(Color(0xFF11152E)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("Всё", "Фото", "Видео").forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == index) Color(0xFF5427D8) else Color.Transparent)
                            .clickable { selectedTab = index }.padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 12.sp, color = Color.White,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(50.dp),
                singleLine = true,
                label = { Text("Поиск по названию") }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp, bottom = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SortButton("Новые", sort == MediaSort.NEWEST) { sort = MediaSort.NEWEST }
                SortButton("Старые", sort == MediaSort.OLDEST) { sort = MediaSort.OLDEST }
                SortButton("По имени", sort == MediaSort.NAME) { sort = MediaSort.NAME }
            }
            Text(
                when {
                    loading -> "Обновляем медиатеку…"
                    search.isNotBlank() -> "Найдено: ${visibleMedia.size}"
                    else -> "Показано: ${visibleMedia.size}"
                },
                color = Color(0xFF5F5A54),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 3.dp)
            )

            if (visibleMedia.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (loading) "Загружаем…"
                        else if (search.isNotBlank()) "Ничего не найдено. Попробуйте другое название."
                        else "В этой категории пока нет файлов."
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleMedia, key = { it.uri.toString() }) { item ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF171B39)),
                            modifier = Modifier.clickable {
                                if (item.isVideo) openVideo(context, item.uri)
                                else selectedPhoto = item
                            },
                        ) {
                            Column {
                                if (item.isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .background(Color(0xFF24234D)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("▶  ВИДЕО", fontWeight = FontWeight.Bold, color = Color(0xFFBDB3FF))
                                    }
                                } else {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = item.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .background(Color(0xFF24234D)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Column(modifier = Modifier.padding(9.dp)) {
                                    Text(item.name, maxLines = 1, fontWeight = FontWeight.Medium)
                                    Text(
                                        formatDate(item.dateSeconds),
                                        color = Color(0xFF8E8AAE),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    updateMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { updateMessage = null },
            confirmButton = { TextButton(onClick = { updateMessage = null }) { Text("Понятно") } },
            title = { Text("Обновление приложения") },
            text = { Text(message) }
        )
    }

    updateInfo?.let { update ->
        AlertDialog(
            onDismissRequest = { if (!downloadingUpdate) updateInfo = null },
            confirmButton = {
                TextButton(enabled = !downloadingUpdate, onClick = {
                    scope.launch {
                        downloadingUpdate = true
                        try {
                            val started = AppUpdater.downloadAndInstall(context, update)
                            if (started) updateInfo = null
                            else updateMessage = "Разреши установку приложений для ФотоПорядка в настройках. Вернись сюда и снова нажми «Скачать и установить»."
                        } catch (error: Exception) {
                            updateMessage = "Не удалось установить обновление: ${error.message ?: "ошибка"}"
                        } finally { downloadingUpdate = false }
                    }
                }) { Text(if (downloadingUpdate) "Скачиваем…" else "Скачать и установить") }
            },
            dismissButton = { TextButton(onClick = { updateInfo = null }, enabled = !downloadingUpdate) { Text("Позже") } },
            title = { Text("Доступна версия ${update.versionName}") },
            text = { Text(update.notes.ifBlank { "Доступна новая версия приложения." }) }
        )
    }

    selectedPhoto?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedPhoto = null },
            confirmButton = {
                TextButton(onClick = { selectedPhoto = null }) { Text("Закрыть") }
            },
            title = { Text(item.name, maxLines = 1) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Text("Дата: ${formatDate(item.dateSeconds)}")
                }
            }
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, accent: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171B39))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(label, color = Color(0xFFAAA6C7), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SortButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(30.dp),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFAAA6C7),
            fontSize = 11.sp
        )
    }
}

private fun requiredMediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaPermission(context: Context): Boolean {
    return requiredMediaPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun loadMedia(context: Context): List<MediaItem> {
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.MediaColumns.DATE_TAKEN
    )
    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
    val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
    )
    val result = mutableListOf<MediaItem>()

    context.contentResolver.query(
        collection,
        projection,
        selection,
        selectionArgs,
        "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val takenColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val isVideo = cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val taken = if (takenColumn >= 0) cursor.getLong(takenColumn) else 0L
            val added = cursor.getLong(addedColumn)
            val baseUri = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            result += MediaItem(
                uri = ContentUris.withAppendedId(baseUri, id),
                name = cursor.getString(nameColumn) ?: if (isVideo) "Видео" else "Фото",
                dateSeconds = if (taken > 0) taken / 1000 else added,
                isVideo = isVideo
            )
        }
    }
    return result
}

private fun openVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

private fun formatDate(seconds: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(seconds * 1000))
