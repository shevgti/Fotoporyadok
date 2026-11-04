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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.draw.shadow
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

private data class AppNotificationRecord(val id: Long, val title: String, val message: String, val time: Long, val type: String, val read: Boolean)

private enum class MediaSort { NEWEST, OLDEST, NAME }

internal data class AppTheme(
    val id: Int, val name: String,
    val background: Color, val backgroundEnd: Color,
    val surface: Color, val card: Color,
    val primary: Color, val secondary: Color, val accent: Color,
    val muted: Color, val text: Color, val onPrimary: Color,
    val isDark: Boolean, val wallpaperUrl: String
)

// Фотофоны взяты с PxHere и опубликованы под CC0 / Public Domain.
private val appThemes = listOf(
    AppTheme(0, "Apple — Сьерра", Color(0xFFF2F2F7), Color.White, Color(0xFFF8F8FC), Color.White, Color(0xFF007AFF), Color(0xFF5AC8FA), Color(0xFF007AFF), Color(0xFF8E8E93), Color(0xFF1C1C1E), Color.White, false, "https://c.pxhere.com/photos/18/5e/teton_mountains_wyoming_trees_forest_woods_river_water-1119978.jpg!d"),
    AppTheme(1, "Apple — Ночь", Color(0xFF090B12), Color(0xFF171B27), Color(0xFF171B27), Color(0xFF252A38), Color(0xFF0A84FF), Color(0xFF64D2FF), Color(0xFF0A84FF), Color(0xFF969EAE), Color(0xFFF5F5F7), Color.White, true, "https://c.pxhere.com/photos/59/e8/beach_clouds_conifer_daylight_docks_foliage_forest_island-1068766.jpg!d"),
    AppTheme(2, "Океан", Color(0xFF071C2C), Color(0xFF123B54), Color(0xFF123B54), Color(0xFF1B4A64), Color(0xFF28B8D5), Color(0xFF72DCEB), Color(0xFF28B8D5), Color(0xFF9BBAC8), Color(0xFFF1FBFF), Color(0xFF06202B), true, "https://c.pxhere.com/photos/54/6b/trees_sky_mountain_clouds_forest_landscape_outdoor_turquoise-106152.jpg!d"),
    AppTheme(3, "Туманный лес", Color(0xFFE9EEF0), Color(0xFFF8FAFB), Color(0xFFF7FAFB), Color.White, Color(0xFF587C86), Color(0xFF9BB8B9), Color(0xFF587C86), Color(0xFF74858A), Color(0xFF18282D), Color.White, false, "https://c.pxhere.com/photos/00/cb/lake_forest_mountain_foggy_landscape_nature_water_evergreens-595654.jpg!d"),
    AppTheme(4, "Золотой час", Color(0xFF201A1B), Color(0xFF3B2B2B), Color(0xFF382B2B), Color(0xFF493535), Color(0xFFFF9A62), Color(0xFFFFC078), Color(0xFFFF9A62), Color(0xFFC3A9A1), Color(0xFFFFF4EE), Color(0xFF2A1712), true, "https://c.pxhere.com/photos/6b/5a/lake_reflection_mountains_landscape_calm_nature_water_peaceful-676105.jpg!d"),
    AppTheme(5, "Северное сияние", Color(0xFF101C2C), Color(0xFF1B3045), Color(0xFF1B3045), Color(0xFF29445A), Color(0xFF58C7B7), Color(0xFF86E0D3), Color(0xFF58C7B7), Color(0xFF9CB6C4), Color(0xFFF0FBFF), Color(0xFF09211F), true, "https://c.pxhere.com/photos/5f/66/mountain_sky_clouds_outdoors_tranquil_high_weather_meteorology-1015434.jpg!d"),
    AppTheme(6, "Тропики", Color(0xFFF1F5EA), Color.White, Color(0xFFF9FCF4), Color.White, Color(0xFF218C70), Color(0xFF7BC9A7), Color(0xFF218C70), Color(0xFF7C8D7F), Color(0xFF18372C), Color.White, false, "https://c.pxhere.com/photos/d4/19/palm_trees_tropical_houses_sky_clouds_city_town_sunset-892422.jpg!d"),
    AppTheme(7, "Альпийский берег", Color(0xFFEAF2F8), Color.White, Color(0xFFF7FBFF), Color.White, Color(0xFF367BAA), Color(0xFF82B8D8), Color(0xFF367BAA), Color(0xFF788C9A), Color(0xFF172B3A), Color.White, false, "https://c.pxhere.com/photos/45/0c/mountains_sea_water_buildings_city_vacation_europe_travel-654439.jpg!d"),
    AppTheme(8, "Морской закат", Color(0xFFF6ECE7), Color(0xFFFFF9F5), Color(0xFFFFF8F3), Color.White, Color(0xFFD36E58), Color(0xFFFFB17B), Color(0xFFD36E58), Color(0xFF9A8177), Color(0xFF38251F), Color.White, false, "https://c.pxhere.com/photos/a4/fc/photo-111696.jpg!d"),
    AppTheme(9, "Монохром", Color(0xFF151515), Color(0xFF292929), Color(0xFF292929), Color(0xFF383838), Color(0xFFB7B7B7), Color(0xFFE0E0E0), Color(0xFFB7B7B7), Color(0xFF9B9B9B), Color(0xFFF5F5F5), Color(0xFF181818), true, "https://c.pxhere.com/photos/b0/82/beach_black_and_white_boat_bridge_dawn_dock_dusk_evening-1130756.jpg!d")
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
        // Create the channel at app startup so Android/HyperOS can expose
        // the app's notification controls even before the first notification.
        createAppNotificationChannel(this)
        setContent {
            var selectedThemeId by remember {
                mutableStateOf(getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE).getInt("theme_id", 0).takeIf { it in 0..9 } ?: 0)
            }
            val palette = appThemes[selectedThemeId]
            MaterialTheme(
                colorScheme = if (!palette.isDark) {
                    lightColorScheme(primary = palette.primary, onPrimary = palette.onPrimary, secondary = palette.secondary,
                        background = palette.background, surface = palette.surface, onSurface = palette.text)
                } else {
                    darkColorScheme(primary = palette.primary, onPrimary = palette.onPrimary, secondary = palette.secondary,
                        background = palette.background, surface = palette.surface, onSurface = palette.text)
                }
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
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(appNotificationsEnabled(context)) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var notificationHistory by remember { mutableStateOf(loadNotificationHistory(context)) }
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
                    postAppNotification(
                        context,
                        "Доступно обновление ФотоПорядка",
                        "Новая версия ${latest.versionName} готова к установке. Нажми, чтобы открыть приложение.",
                        1002
                    )
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

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) notificationsEnabled = appNotificationsEnabled(context)
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
                modifier = Modifier.width(290.dp),
                drawerContainerColor = palette.surface,
                drawerContentColor = palette.text
            ) {
                Text("Настройки", modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = palette.text)
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(16.dp)).background(palette.primary)
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp)).background(palette.card)
                        .clickable {
                            notificationHistory = loadNotificationHistory(context)
                            showNotificationCenter = true
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    val unread = notificationHistory.count { !it.read }
                    Text(
                        "📬  Центр уведомлений" + if (unread > 0) "  ·  $unread" else "",
                        color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(16.dp)).background(palette.primary)
                        .clickable { showThemePicker = !showThemePicker }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Text(if (showThemePicker) "🎨  Скрыть оформление ▲" else "🎨  Оформление ▼", color = palette.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (showThemePicker) {
                    Text("Внешний вид", modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp), color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp).padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(appThemes, key = { it.id }) { theme ->
                            Column(
                                modifier = Modifier.fillMaxWidth().height(78.dp)
                                    .shadow(if (themeId == theme.id) 8.dp else 3.dp, RoundedCornerShape(11.dp))
                                    .clip(RoundedCornerShape(11.dp))
                                     .background(theme.surface)
                                    
                                    .clickable { onThemeChange(theme.id) }
                                    .padding(horizontal = 4.dp, vertical = 5.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                AsyncImage(
                                    model = theme.wallpaperUrl,
                                    contentDescription = "Фото темы ${theme.name}",
                                    modifier = Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(7.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Text(theme.name, color = theme.text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(4.dp)).background(theme.primary))
                                if (themeId == theme.id) Text("✓", color = theme.text, fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Text("10 тем с фотографиями. Для загрузки фона нужен интернет.", modifier = Modifier.padding(start = 12.dp, bottom = 4.dp), color = palette.muted, fontSize = 10.sp)
                    TextButton(
                        onClick = {
                            showThemePicker = false
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.align(Alignment.End).padding(end = 12.dp)
                    ) { Text("Готово", color = palette.accent) }
                }
                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(color = palette.muted.copy(alpha = 0.25f), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                Text("ФотоПорядок  •  " + BuildConfig.VERSION_NAME, modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp), color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().shadow(5.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (updateInfo != null) Color(0xFF34C759) else palette.primary)
                            .clickable {
                                scope.launch { drawerState.close() }
                                checkingUpdate = true
                                scope.launch {
                                    try {
                                        updateInfo = AppUpdater.check(BuildConfig.VERSION_CODE)
                                        if (updateInfo == null) updateMessage = "У тебя уже установлена последняя версия."
                                        else showUpdateDialog = true
                                    } catch (error: Exception) {
                                        updateMessage = "Не удалось проверить обновление: " + (error.message ?: "ошибка сети")
                                    } finally { checkingUpdate = false }
                                }
                            }.padding(horizontal = 12.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (updateInfo != null) "↻  Доступно обновление " + updateInfo!!.versionName else "↻  Обновление " + BuildConfig.VERSION_NAME,
                            color = if (updateInfo != null) Color.White else palette.onPrimary,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(
                            modifier = Modifier.weight(1f).shadow(3.dp, RoundedCornerShape(9.dp))
                                .clip(RoundedCornerShape(16.dp)).background(palette.card)
                                .clickable { scope.launch { drawerState.close() }; showAboutDialog = true }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("ⓘ О приложении", color = palette.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Box(
                            modifier = Modifier.weight(1f).shadow(3.dp, RoundedCornerShape(9.dp))
                                .clip(RoundedCornerShape(16.dp)).background(palette.card)
                                .clickable { scope.launch { drawerState.close() }; showExitDialog = true }
                                .padding(horizontal = 6.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("↪ Выход", color = palette.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
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
                    Text("ФОТО И ВИДЕО", fontSize = 10.sp, color = palette.muted, fontWeight = FontWeight.Medium)
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
                    Box(modifier = Modifier.width(132.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (hasNewVersion) Color(0xFF34C759) else palette.surface), contentAlignment = Alignment.Center) {
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
                            color = if (hasNewVersion) Color.White else palette.accent
                        )
                    }
                }
                TextButton(onClick = { refreshKey++ }, enabled = hasPermission && !loading) {
                    Text("↻", fontSize = 22.sp, color = palette.accent)
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
                palette = palette,
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
                StatTile("ВСЕГО", media.size.toString(), Modifier.weight(1f), palette.accent, palette)
                StatTile("ФОТО", media.count { !it.isVideo }.toString(), Modifier.weight(1f), palette.accent, palette)
                StatTile("ВИДЕО", media.count { it.isVideo }.toString(), Modifier.weight(1f), palette.accent, palette)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)
                    .clip(RoundedCornerShape(16.dp)).background(palette.surface).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("Всё", "Фото", "Видео").forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                             .background(if (selectedTab == index) palette.primary else Color.Transparent)
                            .clickable { selectedTab = index }.padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 12.sp, color = if (selectedTab == index) Color.White else palette.muted,
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
                    Text(if (selectedAlbumId == null) "✓ Все альбомы" else "Все альбомы", color = if (selectedAlbumId == null) palette.accent else palette.muted, fontSize = 11.sp)
                }
                albums.forEach { album ->
                    TextButton(onClick = { selectedAlbumId = album.id }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(if (selectedAlbumId == album.id) "✓ ${album.name} (${album.count})" else "${album.name} (${album.count})", color = if (selectedAlbumId == album.id) palette.accent else palette.muted, fontSize = 11.sp, maxLines = 1)
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
                            colors = CardDefaults.cardColors(containerColor = palette.card),
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
                                        Text("▶  ВИДЕО", fontWeight = FontWeight.SemiBold, color = palette.accent)
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
                                    Text(item.name, maxLines = 1, fontWeight = FontWeight.Medium, color = palette.text)
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
                .clip(RoundedCornerShape(16.dp)).background(palette.surface).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("⌂  Главная", "✨  Умная сортировка").forEachIndexed { index, label ->
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                         .background(if (selectedPage == index) palette.primary else Color.Transparent)
                        .clickable { selectedPage = index }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (selectedPage == index) Color.White else palette.muted, fontSize = 12.sp, fontWeight = if (selectedPage == index) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
    }

    if (showNotificationCenter) {
        AlertDialog(
            onDismissRequest = { showNotificationCenter = false },
            title = { Text("Центр уведомлений") },
            text = {
                if (notificationHistory.isEmpty()) {
                    Text("Пока уведомлений нет. Здесь будут появляться сообщения об обновлениях и важных событиях ФотоПорядка.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        lazyItems(notificationHistory, key = { it.id }) { entry ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (entry.read) palette.surface else palette.card)
                                    .clickable {
                                        notificationHistory = markNotificationRead(context, entry.id)
                                    }
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = palette.text, fontSize = 14.sp)
                                    if (!entry.read) Text("НОВОЕ", color = palette.accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(entry.message, color = palette.text, fontSize = 12.sp)
                                Text(
                                    SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale("ru")).format(Date(entry.time)),
                                    color = palette.muted, fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    notificationHistory = markAllNotificationsRead(context)
                }, enabled = notificationHistory.any { !it.read }) { Text("Прочитать все") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationCenter = false }) { Text("Закрыть") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Закрыть") } },
            title = { Text("ФотоПорядок") },
            text = {
                Text(
                    "Версия " + BuildConfig.VERSION_NAME + " (build " + BuildConfig.VERSION_CODE + ")\n\n" +
                    "Удобная медиатека для просмотра и упорядочивания фотографий и видео. " +
                    "Ваши файлы остаются на устройстве.\n\n" +
                    "Обновления приложения публикуются через GitHub."
                )
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Выйти из ФотоПорядка?") },
            text = { Text("Приложение будет закрыто. Ваши фотографии и видео не изменятся.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    (context as? Activity)?.finish()
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Отмена") }
            }
        )
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(palette.primary)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = palette.onPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, accent: Color, palette: AppTheme) {
    Card(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = palette.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                 .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, color = palette.muted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Text(value, color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SortButton(label: String, selected: Boolean, palette: AppTheme, onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(30.dp).clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) palette.primary else palette.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            color = if (selected) Color.White else palette.muted,
            fontSize = 12.sp
        )
    }
}



private const val NOTIFICATION_HISTORY_PREFS = "photoorder_notification_history"
private const val NOTIFICATION_HISTORY_KEY = "items"
private const val MAX_NOTIFICATION_HISTORY = 100

private fun loadNotificationHistory(context: Context): List<AppNotificationRecord> {
    val raw = context.getSharedPreferences(NOTIFICATION_HISTORY_PREFS, Context.MODE_PRIVATE)
        .getString(NOTIFICATION_HISTORY_KEY, "[]") ?: "[]"
    return runCatching {
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            AppNotificationRecord(
                id = item.optLong("id"),
                title = item.optString("title"),
                message = item.optString("message"),
                time = item.optLong("time"),
                type = item.optString("type", "general"),
                read = item.optBoolean("read", false)
            )
        }.sortedByDescending { it.time }
    }.getOrDefault(emptyList())
}

private fun saveNotificationHistory(context: Context, records: List<AppNotificationRecord>) {
    val array = org.json.JSONArray()
    records.take(MAX_NOTIFICATION_HISTORY).forEach { record ->
        array.put(org.json.JSONObject().apply {
            put("id", record.id)
            put("title", record.title)
            put("message", record.message)
            put("time", record.time)
            put("type", record.type)
            put("read", record.read)
        })
    }
    context.getSharedPreferences(NOTIFICATION_HISTORY_PREFS, Context.MODE_PRIVATE)
        .edit().putString(NOTIFICATION_HISTORY_KEY, array.toString()).apply()
}

private fun markNotificationRead(context: Context, id: Long): List<AppNotificationRecord> {
    val updated = loadNotificationHistory(context).map { if (it.id == id) it.copy(read = true) else it }
    saveNotificationHistory(context, updated)
    return updated
}

private fun markAllNotificationsRead(context: Context): List<AppNotificationRecord> {
    val updated = loadNotificationHistory(context).map { it.copy(read = true) }
    saveNotificationHistory(context, updated)
    return updated
}

private fun recordAppNotification(context: Context, title: String, message: String, notificationId: Int) {
    val existing = loadNotificationHistory(context)
    val record = AppNotificationRecord(
        id = System.currentTimeMillis(),
        title = title,
        message = message,
        time = System.currentTimeMillis(),
        type = if (notificationId == 1002) "update" else "general",
        read = false
    )
    saveNotificationHistory(context, listOf(record) + existing)
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
    recordAppNotification(context, title, message, notificationId)
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
