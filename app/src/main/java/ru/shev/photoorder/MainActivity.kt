package ru.shev.photoorder

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class MediaItem(
    val uri: Uri,
    val name: String,
    val dateSeconds: Long,
    val isVideo: Boolean,
    val albumId: String,
    val albumName: String
)

private data class AlbumInfo(val id: String, val name: String, val count: Int)

private enum class MediaSort { NEWEST, OLDEST, NAME }

private data class AppTheme(
    val id: Int, val name: String,
    val background: Color, val surface: Color, val card: Color,
    val primary: Color, val secondary: Color, val accent: Color,
    val muted: Color, val text: Color, val onPrimary: Color
)

private val appThemes = listOf(
    AppTheme(0, "Неоновый синий", Color(0xFF06152D), Color(0xFF102744), Color(0xFF142F50), Color(0xFF087BFF), Color(0xFF00D7FF), Color(0xFF35A7FF), Color(0xFF9BB8D5), Color(0xFFF4FAFF), Color.White),
    AppTheme(1, "Светлая", Color(0xFFF1F6FF), Color(0xFFFFFFFF), Color(0xFFE5EEFA), Color(0xFF1769E8), Color(0xFF48A5FF), Color(0xFF287DEB), Color(0xFF53657D), Color(0xFF17263B), Color.White),
    AppTheme(2, "Закат", Color(0xFF18202B), Color(0xFF27313D), Color(0xFF303B47), Color(0xFFFF9B19), Color(0xFFFF6D45), Color(0xFFFFC15C), Color(0xFFC4B8AC), Color(0xFFFFF8EF), Color(0xFF242424)),
    AppTheme(3, "Фиолетовый неон", Color(0xFF080B20), Color(0xFF151A35), Color(0xFF171B39), Color(0xFF5427D8), Color(0xFF9B5CFF), Color(0xFFB99AFF), Color(0xFFAAA6C7), Color(0xFFF7F4FF), Color.White),
    AppTheme(4, "Осень", Color(0xFF24170F), Color(0xFF3A281C), Color(0xFF493222), Color(0xFFB84A08), Color(0xFFE18A24), Color(0xFFFFBD68), Color(0xFFD2B79D), Color(0xFFFFF3E5), Color.White),
    AppTheme(5, "Чёрный и золото", Color(0xFF080808), Color(0xFF191714), Color(0xFF24211B), Color(0xFFB88922), Color(0xFFE4C36A), Color(0xFFFFD978), Color(0xFFBEB7A7), Color(0xFFFFF8E8), Color(0xFF17130A)),
    AppTheme(6, "Зелёная", Color(0xFF071B12), Color(0xFF10291C), Color(0xFF173725), Color(0xFF168A43), Color(0xFF64C96B), Color(0xFF9AE58C), Color(0xFFA8C5AC), Color(0xFFF0FFF1), Color.White),
    AppTheme(7, "Лавандовая", Color(0xFF241A38), Color(0xFF35264D), Color(0xFF44315D), Color(0xFF8B62C8), Color(0xFFE19ACD), Color(0xFFE9B9F4), Color(0xFFD0C0DC), Color(0xFFFFF5FF), Color.White),
    AppTheme(8, "Ледяная", Color(0xFFEAF5FF), Color(0xFFFFFFFF), Color(0xFFDCEEFF), Color(0xFF0878E8), Color(0xFF49C7F5), Color(0xFF2E9EF0), Color(0xFF526D85), Color(0xFF122B42), Color.White),
    AppTheme(9, "Бирюзовый неон", Color(0xFF03151C), Color(0xFF0B2630), Color(0xFF10333D), Color(0xFF00AFC1), Color(0xFFB22BFF), Color(0xFF36E5E8), Color(0xFF9ABCC4), Color(0xFFF0FFFF), Color(0xFF00191D))
)

@Composable
private fun ThemeChoiceDialog(selectedId: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Темы оформления") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                appThemes.forEach { theme ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(theme.card).clickable { onSelect(theme.id) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(theme.background), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(15.dp).clip(RoundedCornerShape(5.dp)).background(theme.primary))
                        }
                        Text(theme.name, modifier = Modifier.weight(1f), color = theme.text, fontSize = 14.sp)
                        if (selectedId == theme.id) Text("✓", color = theme.secondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = palette.primary,
                    onPrimary = Color.White,
                    secondary = palette.secondary,
                    background = palette.background,
                    surface = palette.surface,
                    onSurface = palette.text
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
    var selectedThemeId by remember { mutableStateOf(context.getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE).getInt("theme_id", 3).coerceIn(0, 9)) }
    val palette = appThemes[selectedThemeId]
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showThemePicker by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(hasMediaPermission(context)) }
    var loading by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(MediaSort.NEWEST) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedViewerIndex by remember { mutableStateOf<Int?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf(0) }
    var sortFolders by remember { mutableStateOf<List<SmartSortFolder>>(emptyList()) }
    var sortStatus by remember { mutableStateOf<String?>(null) }
    var pendingSortFolders by remember { mutableStateOf<List<SmartSortFolder>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasMediaPermission(context)
    }

    val sortWriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                sortStatus = "Распределяем файлы…"
                val outcome = withContext(Dispatchers.IO) {
                    moveSmartSortFolders(context, pendingSortFolders)
                }
                sortStatus = "Готово: перемещено " + outcome.first + ", ошибок: " + outcome.second
                refreshKey++
            }
        } else {
            sortStatus = "Системное разрешение не подтверждено. Файлы не перемещены."
        }
    }

    LaunchedEffect(hasPermission, refreshKey) {
        if (hasPermission) {
            loading = true
            media = withContext(Dispatchers.IO) { loadMedia(context) }
            loading = false
        }
    }

    val albums = remember(media) {
        media.groupBy { it.albumId }.map { (id, items) ->
            AlbumInfo(id, items.firstOrNull()?.albumName?.takeIf { it.isNotBlank() } ?: "Без альбома", items.size)
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }
    val tabMediaBase = when (selectedTab) {
        1 -> media.filter { !it.isVideo }
        2 -> media.filter { it.isVideo }
        else -> media
    }
    val tabMedia = if (selectedAlbumId == null) tabMediaBase else tabMediaBase.filter { it.albumId == selectedAlbumId }
    val visibleMedia = remember(tabMedia, search, sort) {
        val filtered = tabMedia.filter { it.name.contains(search.trim(), ignoreCase = true) }
        when (sort) {
            MediaSort.NEWEST -> filtered.sortedByDescending { it.dateSeconds }
            MediaSort.OLDEST -> filtered.sortedBy { it.dateSeconds }
            MediaSort.NAME -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = palette.surface,
                drawerContentColor = palette.text
            ) {
                Text("Меню", modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.text)
                TextButton(
                    onClick = {
                        scope.launch { drawerState.close() }
                        showThemePicker = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                ) {
                    Text("🎨  Темы оформления", modifier = Modifier.fillMaxWidth(), color = palette.text, fontSize = 16.sp)
                }
            }
        }
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(
                    model = R.drawable.fotoporyadok_mark,
                    contentDescription = "Знак ФотоПорядка",
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).clickable { scope.launch { drawerState.open() } },
                    contentScale = ContentScale.Crop
                )
                Column {
                    Text("ФотоПорядок", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = palette.text)
                    Text("ТВОЯ МЕДИАТЕКА", fontSize = 9.sp, color = palette.accent, fontWeight = FontWeight.Bold)
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
                    Text(if (checkingUpdate) "Проверка…" else "Обновить", fontSize = 13.sp, color = palette.accent)
                }
                TextButton(onClick = { refreshKey++ }, enabled = hasPermission && !loading) {
                    Text("↻", fontSize = 22.sp, color = Color(0xFFB99AFF))
                }
            }
        }
        if (selectedPage != 0) {
            Text(
                "Фото и видео на телефоне — в удобном порядке",
                color = palette.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        if (selectedPage == 1) {
            SmartSortPage(
                media = media,
                folders = sortFolders,
                onAnalyze = {
                    sortFolders = buildSmartSortPlan(media)
                    sortStatus = "План готов. Проверь папки и названия."
                },
                onRename = { id, name ->
                    sortFolders = sortFolders.map { if (it.id == id) it.copy(name = name) else it }
                },
                onConfirm = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        sortStatus = "Для безопасного пакетного переноса нужна версия Android 11 или новее. На этом телефоне файлы не изменены."
                    } else {
                        pendingSortFolders = sortFolders
                        val uris = sortFolders.flatMap { it.items }.map { it.uri }
                        if (uris.isEmpty()) {
                            sortStatus = "Нет файлов для переноса."
                        } else {
                            val request = MediaStore.createWriteRequest(context.contentResolver, uris)
                            sortWriteLauncher.launch(IntentSenderRequest.Builder(request).build())
                        }
                    }
                },
                status = sortStatus,
                modifier = Modifier.weight(1f)
            )
        } else if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = palette.card)
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
                    .clip(RoundedCornerShape(11.dp)).background(palette.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("Всё", "Фото", "Видео").forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == index) palette.primary else Color.Transparent)
                            .clickable { selectedTab = index }.padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 12.sp, color = Color.White,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectedAlbumId = null }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(if (selectedAlbumId == null) "✓ Все альбомы" else "Все альбомы", color = if (selectedAlbumId == null) Color(0xFFB99AFF) else Color(0xFFAAA6C7), fontSize = 11.sp)
                }
                albums.forEach { album ->
                    TextButton(onClick = { selectedAlbumId = album.id }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(if (selectedAlbumId == album.id) "✓ ${album.name} (${album.count})" else "${album.name} (${album.count})", color = if (selectedAlbumId == album.id) Color(0xFFB99AFF) else Color(0xFFAAA6C7), fontSize = 11.sp, maxLines = 1)
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
                color = palette.muted,
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
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleMedia, key = { it.uri.toString() }) { item ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF171B39)),
                            modifier = Modifier.clickable {
                                selectedViewerIndex = visibleMedia.indexOf(item).takeIf { it >= 0 }
                            },
                        ) {
                            Column {
                                if (item.isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .background(palette.surface),
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
                                        color = palette.muted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp)).background(Color(0xFF171B39)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("⌂  Главная", "✨  Умная сортировка").forEachIndexed { index, label ->
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (selectedPage == index) palette.primary else Color.Transparent)
                        .clickable { selectedPage = index }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = Color.White, fontSize = 12.sp, fontWeight = if (selectedPage == index) FontWeight.Bold else FontWeight.Normal) }
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

    if (showThemePicker) {
        ThemeChoiceDialog(
            selectedId = selectedThemeId,
            onSelect = { id ->
                selectedThemeId = id
                context.getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE).edit().putInt("theme_id", id).apply()
            },
            onDismiss = { showThemePicker = false }
        )
    }

    selectedViewerIndex?.let { index ->
        FullscreenMediaViewer(
            media = visibleMedia,
            initialIndex = index,
            onClose = { selectedViewerIndex = null }
        )
    }
    }
}

@Composable
private fun FullscreenMediaViewer(
    media: List<MediaItem>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    if (media.isEmpty()) return
    var currentIndex by remember(media, initialIndex) { mutableStateOf(initialIndex.coerceIn(media.indices)) }
    val item = media[currentIndex]
    var dragDistance by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(currentIndex, media.size) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragDistance < -80f && currentIndex < media.lastIndex) currentIndex++
                            else if (dragDistance > 80f && currentIndex > 0) currentIndex--
                            dragDistance = 0f
                        },
                        onHorizontalDrag = { change, amount ->
                            dragDistance += amount
                            change.consume()
                        }
                    )
                }
        ) {
            if (item.isVideo) {
                val context = LocalContext.current
                val player = remember(item.uri) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(PlayerMediaItem.fromUri(item.uri))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(player) {
                    onDispose { player.release() }
                }
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Color(0x99000000)).statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) { Text("✕ Закрыть", color = Color.White) }
                Text(item.name, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                Text("${currentIndex + 1}/${media.size}", color = Color(0xFFBDB3FF), fontSize = 12.sp)
            }
            if (!item.isVideo) {
                Text(
                    "‹  Свайп для перелистывания  ›",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                )
            }
        }
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
            color = if (selected) palette.primary else palette.muted,
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
        MediaStore.MediaColumns.DATE_TAKEN,
        "bucket_id",
        "bucket_display_name"
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
        val bucketIdColumn = cursor.getColumnIndex("bucket_id")
        val bucketNameColumn = cursor.getColumnIndex("bucket_display_name")

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
                isVideo = isVideo,
                albumId = if (bucketIdColumn >= 0) cursor.getString(bucketIdColumn) ?: "unknown" else "unknown",
                albumName = if (bucketNameColumn >= 0) cursor.getString(bucketNameColumn) ?: "Без альбома" else "Без альбома"
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
