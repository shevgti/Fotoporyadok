package ru.shev.photoorder

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
    val background: Color, val backgroundEnd: Color,
    val surface: Color, val card: Color,
    val primary: Color, val secondary: Color, val accent: Color,
    val muted: Color, val text: Color, val onPrimary: Color
)

private val appThemes = listOf(
    AppTheme(0, "Классический синий", Color(0xFF071A35), Color(0xFF123F73), Color(0xFF102B4D), Color(0xFF173B63), Color(0xFF087BFF), Color(0xFF49C8FF), Color(0xFF8BD9FF), Color(0xFF9BB8D5), Color(0xFFF4FAFF), Color.White),
    AppTheme(1, "Весенняя зелень", Color(0xFF092414), Color(0xFF286B35), Color(0xFF143B23), Color(0xFF1D4C2B), Color(0xFF279447), Color(0xFF9FE870), Color(0xFFD4F6A5), Color(0xFFB7D5B4), Color(0xFFF4FFF0), Color.White),
    AppTheme(2, "Солнечное золото", Color(0xFF4A2607), Color(0xFFE99A16), Color(0xFF74400A), Color(0xFF96510B), Color(0xFFFFC02E), Color(0xFFFFE17A), Color(0xFFFFD46B), Color(0xFFE9D1A5), Color(0xFFFFFAE9), Color(0xFF402000)),
    AppTheme(3, "Ледяная свежесть", Color(0xFF0A2348), Color(0xFF7FBDEB), Color(0xFF183A60), Color(0xFF244E78), Color(0xFF9CCFF8), Color(0xFFE3F5FF), Color(0xFFBDE7FF), Color(0xFFB5D1E7), Color(0xFFF7FCFF), Color(0xFF15304D)),
    AppTheme(4, "Графитовая ночь", Color(0xFF080B12), Color(0xFF242B38), Color(0xFF171D28), Color(0xFF222B39), Color(0xFF52647C), Color(0xFFD5B66D), Color(0xFFE8D49B), Color(0xFF9AA7B8), Color(0xFFF4F6FA), Color(0xFF10151D)),
    AppTheme(5, "Розовый сад", Color(0xFF35102D), Color(0xFFB33F75), Color(0xFF571B46), Color(0xFF742653), Color(0xFFE45D9B), Color(0xFFFFB0D0), Color(0xFFFFD0E2), Color(0xFFE1B6CD), Color(0xFFFFF5FA), Color(0xFF47142F)),
    AppTheme(6, "Бирюзовая лагуна", Color(0xFF033D4B), Color(0xFF08A6B5), Color(0xFF075563), Color(0xFF0A6878), Color(0xFF12C5D2), Color(0xFF9AF4ED), Color(0xFF8CEFE8), Color(0xFFA9D8D9), Color(0xFFF0FFFF), Color(0xFF00343D)),
    AppTheme(7, "Фиолетовый вечер", Color(0xFF090B31), Color(0xFF322078), Color(0xFF171849), Color(0xFF24245F), Color(0xFF533DDA), Color(0xFFB5A5FF), Color(0xFF9B8CFF), Color(0xFFB3B4D8), Color(0xFFF4F3FF), Color.White),
    AppTheme(8, "Кофейное тепло", Color(0xFF24140D), Color(0xFF754322), Color(0xFF3C2419), Color(0xFF543322), Color(0xFFA86A3D), Color(0xFFE2B77E), Color(0xFFF0D1A5), Color(0xFFD0B7A1), Color(0xFFFFF7EF), Color(0xFF2C1A10)),
    AppTheme(9, "Северное сияние", Color(0xFF042B35), Color(0xFF087F78), Color(0xFF0B4148), Color(0xFF10575A), Color(0xFF00B9A7), Color(0xFF8DF5D1), Color(0xFF72E9D5), Color(0xFFA8D6D0), Color(0xFFF0FFFC), Color(0xFF003B3A))
)


private fun themeWallpaperRes(id: Int): Int = when (id) {
    0 -> R.drawable.theme_wallpaper_01
    1 -> R.drawable.theme_wallpaper_02
    2 -> R.drawable.theme_wallpaper_03
    3 -> R.drawable.theme_wallpaper_04
    4 -> R.drawable.theme_wallpaper_05
    5 -> R.drawable.theme_wallpaper_06
    6 -> R.drawable.theme_wallpaper_07
    7 -> R.drawable.theme_wallpaper_08
    8 -> R.drawable.theme_wallpaper_09
    9 -> R.drawable.theme_wallpaper_10
    else -> R.drawable.theme_wallpaper_01
}

private class SlantedButtonShape(private val cut: Float = 0.16f) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * cut, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width * (1f - cut), size.height)
            lineTo(0f, size.height)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var selectedThemeId by remember {
                mutableStateOf(getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE).getInt("theme_id", 0).let { oldId -> when (oldId) { 0 -> 0; 1 -> 1; 2, 9, 14 -> 2; 4 -> 3; 5, 8, 12 -> 4; 6, 13 -> 5; 7, 10, 18 -> 6; 15, 19 -> 7; 17 -> 8; 16 -> 9; else -> 0 } })
            }
            val palette = appThemes[selectedThemeId]
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = palette.primary,
                    onPrimary = palette.onPrimary,
                    secondary = palette.secondary,
                    background = palette.background,
                    surface = palette.surface,
                    onSurface = palette.text
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = palette.background) {
                    PhotoOrderScreen(
                        themeId = selectedThemeId,
                        onThemeChange = { id ->
                            selectedThemeId = id
                            getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE)
                                .edit().putInt("theme_id", id).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoOrderScreen(themeId: Int, onThemeChange: (Int) -> Unit) {
    val context = LocalContext.current
    val palette = appThemes[themeId]
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showThemePicker by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(appNotificationsEnabled(context)) }
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
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf(0) }
    var sortFolders by remember { mutableStateOf<List<SmartSortFolder>>(emptyList()) }
    var sortStatus by remember { mutableStateOf<String?>(null) }
    var pendingSortFolders by remember { mutableStateOf<List<SmartSortFolder>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            checkingUpdate = true
            try {
                val latest = AppUpdater.check(BuildConfig.VERSION_CODE)
                if (latest != null && latest.versionCode != updateInfo?.versionCode) {
                    updateInfo = latest
                    if (appNotificationsEnabled(context)) {
                        postAppNotification(
                            context,
                            "Доступно обновление ФотоПорядка",
                            "Новая версия ${latest.versionName} готова к установке. Нажми, чтобы открыть приложение.",
                            1002
                        )
                    }
                    showUpdateDialog = true
                } else if (latest == null) {
                    updateInfo = null
                }
            } catch (_: Exception) {
                // Ошибка сети не должна мешать работе с медиатекой.
            } finally {
                checkingUpdate = false
            }
            kotlinx.coroutines.delay(30 * 60 * 1000L)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasMediaPermission(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsEnabled = appNotificationsEnabled(context)
        if (notificationsEnabled) {
            createAppNotificationChannel(context)
            postAppNotification(context, "ФотоПорядок", "Уведомления включены. Теперь приложение сможет сообщать о важных событиях.", 1001)
        }
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
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                        .clip(SlantedButtonShape()).background(Brush.horizontalGradient(listOf(palette.primary, palette.secondary)))
                        .clickable {
                            if (Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notificationsEnabled = appNotificationsEnabled(context)
                                if (notificationsEnabled) {
                                    postAppNotification(context, "ФотоПорядок", "Уведомления работают.", 1001)
                                } else {
                                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Text(
                        if (notificationsEnabled) "🔔  Уведомления включены" else "🔕  Включить уведомления",
                        color = palette.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                        .clip(SlantedButtonShape()).background(Brush.horizontalGradient(listOf(palette.primary, palette.secondary)))
                        .clickable { showThemePicker = !showThemePicker }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Text(if (showThemePicker) "🎨  Скрыть скины ▲" else "🎨  Скины и темы оформления ▼", color = palette.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (showThemePicker) {
                    Text("Выбери оформление", modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp), color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp).padding(horizontal = 10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(appThemes, key = { it.id }) { theme ->
                            Column(
                                modifier = Modifier.fillMaxWidth().height(112.dp)
                                    .clip(SlantedButtonShape())
                                    .background(Brush.verticalGradient(listOf(theme.backgroundEnd, theme.background)))
                                    .paint(painterResource(themeWallpaperRes(theme.id)), contentScale = ContentScale.Crop, alpha = 0.62f)
                                    .clickable { onThemeChange(theme.id) }
                                    .padding(horizontal = 9.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text((theme.id + 1).toString().padStart(2, '0') + " · " + theme.name, color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        listOf(theme.primary, theme.secondary, theme.accent).forEach { shade ->
                                            Box(Modifier.weight(1f).height(6.dp).clip(SlantedButtonShape()).background(shade))
                                        }
                                    }
                                }
                                if (themeId == theme.id) Text("✓ Выбрано", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    TextButton(onClick = { showThemePicker = false }, modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) { Text("Готово", color = palette.accent) }
                }
            }
        }
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.backgroundEnd, palette.background)))
            .paint(painterResource(themeWallpaperRes(themeId)), contentScale = ContentScale.Crop, alpha = 0.16f)
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
                val hasNewVersion = updateInfo != null
                TextButton(
                    onClick = {
                        if (hasNewVersion) {
                            showUpdateDialog = true
                        } else {
                            scope.launch {
                                checkingUpdate = true
                                updateMessage = null
                                try {
                                    updateInfo = AppUpdater.check(BuildConfig.VERSION_CODE)
                                    if (updateInfo == null) updateMessage = "У тебя уже установлена последняя версия."
                                    else showUpdateDialog = true
                                } catch (error: Exception) {
                                    updateMessage = "Не удалось проверить обновление: ${error.message ?: "ошибка сети"}"
                                } finally { checkingUpdate = false }
                            }
                        }
                    },
                    enabled = !checkingUpdate && !downloadingUpdate,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Box(modifier = Modifier.width(132.dp).height(36.dp).clip(SlantedButtonShape()).background(if (hasNewVersion) Color(0xFFA8E85A) else palette.surface), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                checkingUpdate -> "Проверка…"
                                hasNewVersion -> "Доступно ${updateInfo?.versionName}"
                                else -> "Обновление ${BuildConfig.VERSION_NAME}"
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            fontSize = 10.sp, maxLines = 1, softWrap = false,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            fontWeight = if (hasNewVersion) FontWeight.Bold else FontWeight.Medium,
                            color = if (hasNewVersion) Color(0xFF17210A) else palette.accent
                        )
                    }
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
                    SlantedActionButton("Выбрать фото и видео", palette) { permissionLauncher.launch(requiredMediaPermissions()) }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                StatTile("ВСЕГО", media.size.toString(), Modifier.weight(1f), Color(0xFF5427D8))
                StatTile("ФОТО", media.count { !it.isVideo }.toString(), Modifier.weight(1f), palette.secondary)
                StatTile("ВИДЕО", media.count { it.isVideo }.toString(), Modifier.weight(1f), palette.accent)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)
                    .clip(SlantedButtonShape()).background(palette.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("Всё", "Фото", "Видео").forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier.weight(1f).clip(SlantedButtonShape())
                            .background(if (selectedTab == index) Brush.horizontalGradient(listOf(palette.primary, palette.secondary)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
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
                SortButton("Новые", sort == MediaSort.NEWEST, palette) { sort = MediaSort.NEWEST }
                SortButton("Старые", sort == MediaSort.OLDEST, palette) { sort = MediaSort.OLDEST }
                SortButton("По имени", sort == MediaSort.NAME, palette) { sort = MediaSort.NAME }
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
                                        Text("▶  ВИДЕО", fontWeight = FontWeight.Bold, color = palette.accent)
                                    }
                                } else {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = item.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .background(palette.surface),
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
                .clip(SlantedButtonShape()).background(palette.surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("⌂  Главная", "✨  Умная сортировка").forEachIndexed { index, label ->
                Box(
                    modifier = Modifier.weight(1f).clip(SlantedButtonShape())
                        .background(if (selectedPage == index) Brush.horizontalGradient(listOf(palette.primary, palette.secondary)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
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

    updateInfo?.takeIf { showUpdateDialog }?.let { update ->
        AlertDialog(
            onDismissRequest = { if (!downloadingUpdate) showUpdateDialog = false },
            confirmButton = {
                TextButton(enabled = !downloadingUpdate, onClick = {
                    scope.launch {
                        downloadingUpdate = true
                        try {
                            val started = AppUpdater.downloadAndInstall(context, update)
                            if (started) { updateInfo = null; showUpdateDialog = false }
                            else updateMessage = "Разреши установку приложений для ФотоПорядка в настройках. Вернись сюда и снова нажми «Скачать и установить»."
                        } catch (error: Exception) {
                            updateMessage = "Не удалось установить обновление: ${error.message ?: "ошибка"}"
                        } finally { downloadingUpdate = false }
                    }
                }) { Text(if (downloadingUpdate) "Скачиваем…" else "Скачать и установить") }
            },
            dismissButton = { TextButton(onClick = { updateInfo = null }, enabled = !downloadingUpdate) { Text("Позже") } },
            title = { Text("Доступна версия ${update.versionName}") },
            text = { Text((update.notes.substringBefore("\n\n").ifBlank { "Доступна новая версия приложения." }) + "\n\nФайл: app-release.apk\nИсточник: GitHub → Releases → ФотоПорядок " + update.versionName) }
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
private fun SlantedActionButton(label: String, palette: AppTheme, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(SlantedButtonShape())
            .background(Brush.horizontalGradient(listOf(palette.primary, palette.secondary)))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = palette.onPrimary, fontWeight = FontWeight.Bold)
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
private fun SortButton(label: String, selected: Boolean, palette: AppTheme, onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(30.dp).clip(SlantedButtonShape())
            .background(
                if (selected) Brush.horizontalGradient(listOf(palette.primary, palette.secondary))
                else Brush.horizontalGradient(listOf(palette.surface, palette.card))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            color = if (selected) palette.onPrimary else palette.muted,
            fontSize = 11.sp
        )
    }
}


private const val APP_NOTIFICATION_CHANNEL = "photoorder_updates"

private fun appNotificationsEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun createAppNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            APP_NOTIFICATION_CHANNEL,
            "Уведомления ФотоПорядка",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Обновления приложения и важные события медиатеки"
        }
        manager.createNotificationChannel(channel)
    }
}

private fun postAppNotification(context: Context, title: String, message: String, notificationId: Int) {
    if (!appNotificationsEnabled(context)) return
    createAppNotificationChannel(context)
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pendingIntent = launchIntent?.let {
        PendingIntent.getActivity(
            context, notificationId, it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    val notification = NotificationCompat.Builder(context, APP_NOTIFICATION_CHANNEL)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
        .build()
    try {
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    } catch (_: SecurityException) {
        // Permission can be revoked in system settings at any time.
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
