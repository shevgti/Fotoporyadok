package ru.shev.photoorder

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.view.View
import android.provider.MediaStore
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.math.abs
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

private enum class MediaSort { NEWEST, OLDEST }

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


internal const val EXTRA_OPEN_UPDATE_DIALOG = "ru.shev.photoorder.extra.OPEN_UPDATE_DIALOG"
internal const val EXTRA_UPDATE_VERSION_CODE = "ru.shev.photoorder.extra.UPDATE_VERSION_CODE"
internal const val EXTRA_UPDATE_VERSION_NAME = "ru.shev.photoorder.extra.UPDATE_VERSION_NAME"
internal const val EXTRA_UPDATE_DOWNLOAD_URL = "ru.shev.photoorder.extra.UPDATE_DOWNLOAD_URL"
internal const val EXTRA_UPDATE_NOTES = "ru.shev.photoorder.extra.UPDATE_NOTES"

internal fun saveNotificationUpdate(context: Context, update: UpdateInfo) {
    context.getSharedPreferences("photoorder_update_cache", Context.MODE_PRIVATE).edit()
        .putInt(EXTRA_UPDATE_VERSION_CODE, update.versionCode)
        .putString(EXTRA_UPDATE_VERSION_NAME, update.versionName)
        .putString(EXTRA_UPDATE_DOWNLOAD_URL, update.downloadUrl)
        .putString(EXTRA_UPDATE_NOTES, update.notes)
        .apply()
}

private fun readCachedNotificationUpdate(context: Context): UpdateInfo? {
    val prefs = context.getSharedPreferences("photoorder_update_cache", Context.MODE_PRIVATE)
    val versionCode = prefs.getInt(EXTRA_UPDATE_VERSION_CODE, 0)
    val versionName = prefs.getString(EXTRA_UPDATE_VERSION_NAME, null).orEmpty()
    val downloadUrl = prefs.getString(EXTRA_UPDATE_DOWNLOAD_URL, null).orEmpty()
    if (versionCode <= BuildConfig.VERSION_CODE || versionName.isBlank() || downloadUrl.isBlank()) return null
    return UpdateInfo(versionCode, versionName, downloadUrl, prefs.getString(EXTRA_UPDATE_NOTES, "").orEmpty())
}

class MainActivity : ComponentActivity() {
    private var openUpdateDialogRequest by mutableStateOf(false)
    private var notificationUpdateInfo by mutableStateOf<UpdateInfo?>(null)

    private fun readNotificationUpdate(intent: Intent?): UpdateInfo? {
        if (!getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE).getBoolean("auto_update_enabled", false)) return null
        if (intent?.getBooleanExtra(EXTRA_OPEN_UPDATE_DIALOG, false) != true) return null
        val versionCode = intent.getIntExtra(EXTRA_UPDATE_VERSION_CODE, 0)
        val versionName = intent.getStringExtra(EXTRA_UPDATE_VERSION_NAME).orEmpty()
        val downloadUrl = intent.getStringExtra(EXTRA_UPDATE_DOWNLOAD_URL).orEmpty()
        if (versionCode > BuildConfig.VERSION_CODE && versionName.isNotBlank() && downloadUrl.isNotBlank()) {
            return UpdateInfo(versionCode, versionName, downloadUrl,
                intent.getStringExtra(EXTRA_UPDATE_NOTES).orEmpty())
        }
        return readCachedNotificationUpdate(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationUpdateInfo = readNotificationUpdate(intent)
        val automaticUpdatesEnabled = getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE)
            .getBoolean("auto_update_enabled", false)
        openUpdateDialogRequest = automaticUpdatesEnabled &&
            (intent?.getBooleanExtra(EXTRA_OPEN_UPDATE_DIALOG, false) ?: false)
        UpdateCheckWorker.schedule(this)
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
                        lifecycle = lifecycle,
                        themeId = selectedThemeId,
                        openUpdateDialogRequest = openUpdateDialogRequest,
                        notificationUpdateInfo = notificationUpdateInfo,
                        onUpdateDialogRequestHandled = {
                            openUpdateDialogRequest = false
                            notificationUpdateInfo = null
                        },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val automaticUpdatesEnabled = getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE)
            .getBoolean("auto_update_enabled", false)
        if (automaticUpdatesEnabled && intent.getBooleanExtra(EXTRA_OPEN_UPDATE_DIALOG, false)) {
            notificationUpdateInfo = readNotificationUpdate(intent)
            openUpdateDialogRequest = notificationUpdateInfo != null
        }
    }
}

@Composable
private fun PhotoOrderScreen(
    lifecycle: Lifecycle,
    themeId: Int,
    openUpdateDialogRequest: Boolean,
    notificationUpdateInfo: UpdateInfo?,
    onUpdateDialogRequestHandled: () -> Unit,
    onThemeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val palette = appThemes[themeId]
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showThemePicker by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(appNotificationsEnabled(context)) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    var showUpdateSettings by remember { mutableStateOf(false) }
    var autoUpdateEnabled by remember { mutableStateOf(context.getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE).getBoolean("auto_update_enabled", false)) }
    var appNotificationPreference by remember { mutableStateOf(context.getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE).getBoolean("notifications_enabled", true)) }
    var autoUpdateAttemptedVersion by remember { mutableStateOf(0) }
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(hasMediaPermission(context)) }
    var loading by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(MediaSort.NEWEST) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedViewerIndex by remember { mutableStateOf<Int?>(null) }
    var viewerMedia by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var gridCellMinWidth by remember { mutableStateOf(140f) }
    var zoomedMainPhoto by remember { mutableStateOf<MediaItem?>(null) }
    var mainPreviewHeld by remember { mutableStateOf(false) }
    val mainPreviewProgress by animateFloatAsState(
        targetValue = if (mainPreviewHeld) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "mainGalleryPhotoPreviewProgress"
    )
    var refreshKey by remember { mutableStateOf(0) }
    // Seed dialog state from notification data once; later intent updates are applied by the effect below.
    // Keep this state independent from notificationUpdateInfo, which is cleared after handling.
    var updateInfo by remember { mutableStateOf(notificationUpdateInfo) }
    var showUpdateDialog by remember {
        mutableStateOf(openUpdateDialogRequest && notificationUpdateInfo != null)
    }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var updateDownloadedBytes by remember { mutableStateOf(0L) }
    var updateTotalBytes by remember { mutableStateOf(0L) }
    var selectedPage by remember { mutableStateOf(0) }
    val pagePagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    LaunchedEffect(selectedPage) {
        if (pagePagerState.currentPage != selectedPage) pagePagerState.animateScrollToPage(selectedPage)
    }
    LaunchedEffect(pagePagerState.currentPage) { selectedPage = pagePagerState.currentPage }
    var sortFolders by remember { mutableStateOf<List<SmartSortFolder>>(emptyList()) }
    var sortStatus by remember { mutableStateOf<String?>(null) }
    var pendingSortFolders by remember { mutableStateOf<List<SmartSortFolder>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val updatePulseTransition = rememberInfiniteTransition(label = "updateButtonPulse")
    val updatePulse by updatePulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "updateButtonPulseValue"
    )

    // Nested AI navigation is handled by AiOrganizerPage. On the root
    // screen, system Back asks for confirmation instead of closing by accident.
    // Android's bottom-up Home gesture is not a Back event and remains untouched.
    androidx.activity.compose.BackHandler(
        enabled = selectedViewerIndex != null || drawerState.isOpen || selectedPage == 0
    ) {
        when {
            zoomedMainPhoto != null -> { zoomedMainPhoto = null; mainPreviewHeld = false }
            selectedViewerIndex != null -> selectedViewerIndex = null
            drawerState.isOpen -> scope.launch { drawerState.close() }
            selectedPage == 0 -> showExitDialog = true
        }
    }

    LaunchedEffect(openUpdateDialogRequest, notificationUpdateInfo) {
        if (openUpdateDialogRequest) {
            // Do not block the dialog on a network round-trip. The release payload is
            // supplied by the notification and also persisted as a local fallback.
            val update = notificationUpdateInfo
            if (update != null) {
                updateInfo = update
                showUpdateDialog = true
            } else if (updateInfo == null) {
                updateMessage = "Не удалось прочитать данные обновления из уведомления. Открой приложение и проверь обновления вручную."
            }
            onUpdateDialogRequestHandled()
        }
    }

    // Check immediately whenever the app becomes visible again. While it stays
    // in the foreground, keep the 30-minute safety refresh as before.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                checkingUpdate = true
                try {
                    val latest = AppUpdater.check(BuildConfig.VERSION_CODE)
                    if (latest != null) {
                        // Keep the update available in the header until it is actually installed.
                        updateInfo = latest
                        if (autoUpdateEnabled && autoUpdateAttemptedVersion != latest.versionCode) {
                            autoUpdateAttemptedVersion = latest.versionCode
                            downloadingUpdate = true
                            try {
                                val started = AppUpdater.downloadAndInstall(context, latest) { downloaded, total ->
                                    updateDownloadedBytes = downloaded
                                    updateTotalBytes = total
                                }
                                if (started) updateInfo = null
                                else updateMessage = "Разреши установку приложений для ФотоПорядка в настройках Android, затем снова открой приложение."
                            } catch (error: Exception) {
                                updateMessage = "Автоматическая загрузка не удалась: ${error.message ?: error.javaClass.simpleName}. Можно повторить вручную."
                            } finally { downloadingUpdate = false }
                        } else if (!autoUpdateEnabled) {
                            // Manual mode is silent: only the persistent header button announces the update.
                            showUpdateDialog = false
                        }
                    }
                } catch (_: Exception) {
                    // Ошибка сети не должна мешать работе с медиатекой.
                } finally {
                    checkingUpdate = false
                }
                val updateCheckIntervalMs = if (UpdateCheckWorker.FAST_TEST_MODE) {
                    UpdateCheckWorker.TEST_INTERVAL_MINUTES * 60_000L
                } else {
                    30 * 60 * 1000L
                }
                kotlinx.coroutines.delay(updateCheckIntervalMs)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasMediaPermission(context)
    }

    // Автоматически перечитываем медиатеку, когда Android сообщает об изменениях.
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refreshKey++
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
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

    val tabMedia = when (selectedTab) {
        1 -> media.filter { !it.isVideo }
        2 -> media.filter { it.isVideo }
        else -> media
    }
    val visibleMedia = remember(tabMedia, sort) {
        when (sort) {
            MediaSort.NEWEST -> tabMedia.sortedByDescending { it.dateSeconds }
            MediaSort.OLDEST -> tabMedia.sortedBy { it.dateSeconds }
        }
    }
    val allSortedMedia = remember(media, sort) {
        when (sort) {
            MediaSort.NEWEST -> media.sortedByDescending { it.dateSeconds }
            MediaSort.OLDEST -> media.sortedBy { it.dateSeconds }
        }
    }

    ModalNavigationDrawer(
        gesturesEnabled = true,
        scrimColor = Color.Black.copy(alpha = 0.48f),
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
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp)).background(palette.primary)
                        .clickable { notificationsEnabled = appNotificationsEnabled(context); showNotificationSettings = !showNotificationSettings }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Text((if (notificationsEnabled && appNotificationPreference) "🔔  Уведомления включены" else "🔕  Уведомления выключены") +
                        if (showNotificationSettings) "  ▲" else "  ▼",
                        color = palette.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (showNotificationSettings) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (systemNotificationsAllowed(context)) "Системное разрешение Android: включено" else "Системное разрешение Android: выключено",
                            color = palette.muted, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.card)
                            .clickable {
                                val next = !appNotificationPreference
                                appNotificationPreference = next
                                context.getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE).edit().putBoolean("notifications_enabled", next).apply()
                                if (next && !appNotificationsEnabled(context)) {
                                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    })
                                }
                                notificationsEnabled = appNotificationsEnabled(context)
                            }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Разрешить уведомления ФотоПорядка", color = palette.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (appNotificationPreference) "В приложении включены" else "В приложении отключены", color = palette.muted, fontSize = 10.sp)
                            }
                            Text(if (appNotificationPreference) "ВКЛ" else "ВЫКЛ", color = if (appNotificationPreference) palette.accent else palette.muted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        if (!notificationsEnabled) TextButton(onClick = {
                            context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            })
                        }) { Text("Открыть настройки Android") }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).shadow(4.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)).background(palette.primary)
                    .clickable { showUpdateSettings = !showUpdateSettings }.padding(horizontal = 14.dp, vertical = 13.dp)) {
                    Text(if (showUpdateSettings) "↻  Обновления ▲" else "↻  Обновления ▼",
                        color = palette.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (showUpdateSettings) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Как устанавливать новые версии?", color = palette.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        listOf(false to "Спрашивать перед обновлением", true to "Автоматически скачивать обновления").forEach { (enabled, label) ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.card)
                                .clickable {
                                    autoUpdateEnabled = enabled
                                    context.getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE).edit().putBoolean("auto_update_enabled", enabled).apply()
                                    UpdateCheckWorker.schedule(context)
                                }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (autoUpdateEnabled == enabled) "◉" else "○", color = palette.accent, fontSize = 17.sp)
                                Text(label, color = palette.text, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        Text(
                            if (UpdateCheckWorker.FAST_TEST_MODE)
                                "ТЕСТОВЫЙ РЕЖИМ: проверка обновлений примерно каждые 2 минуты. После теста интервал будет возвращён."
                            else
                                "При обнаружении новой версии приложение автоматически скачает APK, когда оно открыто. Перед установкой Android всё равно попросит подтверждение.",
                            color = palette.muted, fontSize = 10.sp
                        )
                        val workerPrefs = context.getSharedPreferences("photoorder_update_worker", Context.MODE_PRIVATE)
                        val lastWorkerStatus = workerPrefs.getString("last_check_status", "Фоновая проверка ещё не запускалась")
                            ?: "Фоновая проверка ещё не запускалась"
                        val lastWorkerTime = workerPrefs.getLong("last_check_timestamp", 0L)
                        val lastWorkerTimeText = if (lastWorkerTime > 0L)
                            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(lastWorkerTime))
                        else "нет данных"
                        Text("Диагностика фоновой проверки:\n$lastWorkerStatus\nВремя: $lastWorkerTimeText",
                            color = palette.muted, fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
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
                                    model = themeWallpaperRes(theme.id),
                                    contentDescription = "Фото темы ${theme.name}",
                                    modifier = Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(7.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Text(theme.name, color = theme.text, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(4.dp)).background(theme.primary))
                                if (themeId == theme.id) Text("✓", color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.Black)
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
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            }.padding(horizontal = 12.dp, vertical = if (updateInfo != null) 14.dp else 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (updateInfo != null) "●  Доступно новое обновление · " + updateInfo!!.versionName else "↻  Обновление " + BuildConfig.VERSION_NAME,
                            color = if (updateInfo != null) Color.White else palette.onPrimary,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = themeWallpaperRes(themeId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent, palette.background.copy(alpha = 0.52f))
                )
            )
        )
        Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(drawerState.isOpen, selectedViewerIndex, zoomedMainPhoto) {
                val edgeSize = 32.dp.toPx()
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.position.x <= edgeSize && !drawerState.isOpen &&
                        selectedViewerIndex == null && zoomedMainPhoto == null
                    ) {
                        var total = Offset.Zero
                        var opened = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null && change.pressed) {
                                total += change.position - change.previousPosition
                                if (total.x > touchSlop && abs(total.x) > abs(total.y) * 1.2f) {
                                    change.consume()
                                    opened = true
                                    scope.launch { drawerState.open() }
                                } else if (total.x < -touchSlop || abs(total.y) > touchSlop) {
                                    break
                                }
                            }
                        } while (event.changes.any { it.pressed } && !opened)
                    }
                }
            }
            .background(palette.background.copy(alpha = 0.64f))
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.clickable { scope.launch { drawerState.open() } }
                ) {
                    Box(
                        modifier = Modifier.size(42.dp)
                            .shadow(3.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.card)
                            .border(1.5.dp, palette.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = R.drawable.fotoporyadok_mark,
                            contentDescription = "Открыть меню",
                            modifier = Modifier.size(29.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text("МЕНЮ", fontSize = 8.sp, lineHeight = 9.sp,
                        fontWeight = FontWeight.ExtraBold, color = palette.primary)
                }
                Column {
                    Text("ФотоПорядок", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = palette.text)
                    Text("ФОТО И ВИДЕО", fontSize = 10.sp, color = palette.muted, fontWeight = FontWeight.Medium)
                }
            }
            if (updateInfo != null) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .graphicsLayer {
                            val scale = 1f + updatePulse * 0.035f
                            scaleX = scale
                            scaleY = scale
                        }
                        .shadow((8f + updatePulse * 12f).dp, RoundedCornerShape(11.dp))
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(
                                        red = 0.02f + updatePulse * 0.05f,
                                        green = 0.42f + updatePulse * 0.35f,
                                        blue = 0.18f + updatePulse * 0.16f
                                    ),
                                    Color(
                                        red = 0.01f + updatePulse * 0.05f,
                                        green = 0.62f + updatePulse * 0.35f,
                                        blue = 0.28f + updatePulse * 0.18f
                                    ),
                                    Color(
                                        red = 0.01f + updatePulse * 0.04f,
                                        green = 0.35f + updatePulse * 0.32f,
                                        blue = 0.14f + updatePulse * 0.18f
                                    )
                                )
                            )
                        )
                        .clickable(enabled = !downloadingUpdate) { showUpdateDialog = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            "● Обновление",
                            color = Color.White,
                            fontSize = 8.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Text(
                            "Версия ${updateInfo?.versionName}",
                            color = Color.White.copy(alpha = 0.94f),
                            fontSize = 8.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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

        HorizontalPager(
            state = pagePagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = selectedViewerIndex == null && zoomedMainPhoto == null,
            beyondViewportPageCount = 1
        ) { page ->
            val pageOffset = (page - pagePagerState.currentPage - pagePagerState.currentPageOffsetFraction)
                .coerceIn(-1f, 1f)
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    rotationY = pageOffset * 90f
                    transformOrigin = TransformOrigin(if (pageOffset > 0f) 0f else 1f, 0.5f)
                    cameraDistance = 24f * density
                    alpha = 1f - abs(pageOffset) * 0.12f
                }
            ) {
                if (page == 1 && hasPermission) {
                    AiOrganizerPage(
                        media = media,
                        palette = palette,
                        modifier = Modifier.fillMaxSize(),
                        onExit = { scope.launch { pagePagerState.animateScrollToPage(0) } },
                        onOpenMedia = { folderMedia, selected ->
                            // Keep the viewer strictly inside the album/category that was opened.
                            viewerMedia = folderMedia
                            selectedViewerIndex = folderMedia.indexOfFirst { it.uri == selected.uri }.takeIf { it >= 0 }
                        }
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
        if (!hasPermission) {
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
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
                    .shadow(7.dp, RoundedCornerShape(15.dp))
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.verticalGradient(listOf(palette.card, palette.surface, palette.card)))
                    .border(1.dp, Color.White.copy(alpha = 0.62f), RoundedCornerShape(15.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Всё", "Фото", "Видео").forEachIndexed { index, label ->
                    val active = selectedTab == index
                    Box(
                        modifier = Modifier.weight(1f)
                            .shadow(if (active) 5.dp else 0.dp, SlantedButtonShape(0.12f))
                            .clip(SlantedButtonShape(0.12f))
                            .background(
                                if (active) Brush.verticalGradient(listOf(palette.secondary, palette.primary))
                                else Brush.verticalGradient(listOf(palette.card, palette.surface))
                            )
                            .clickable { selectedTab = index }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 12.sp, color = if (active) palette.onPrimary else palette.muted,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp, bottom = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SortButton("Новые", sort == MediaSort.NEWEST, palette) { sort = MediaSort.NEWEST }
                SortButton("Старые", sort == MediaSort.OLDEST, palette) { sort = MediaSort.OLDEST }
            }
            Text(
                when {
                    loading -> "Обновляем медиатеку…"
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
                        else "В этой категории пока нет файлов."
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Use the same adaptive-grid calculation as LazyVerticalGrid so captions
                    // disappear as soon as the layout reaches three or more columns.
                    val gridColumnCount = ((maxWidth + 10.dp) / (gridCellMinWidth.dp + 10.dp)).toInt().coerceAtLeast(1)
                    LazyVerticalGrid(
                    columns = GridCells.Adaptive(gridCellMinWidth.dp),
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        // Observe pinch gestures in the Initial pass so child photo
                        // tap handlers cannot steal the second pointer.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var previousSpan = 0f
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    val centerX = pressed.map { it.position.x }.average().toFloat()
                                    val centerY = pressed.map { it.position.y }.average().toFloat()
                                    val span = pressed.map { change ->
                                        val dx = change.position.x - centerX
                                        val dy = change.position.y - centerY
                                        sqrt(dx * dx + dy * dy)
                                    }.average().toFloat()
                                    if (previousSpan > 0f && span > 0f) {
                                        val scale = span / previousSpan
                                        if (abs(scale - 1f) > 0.001f) {
                                            gridCellMinWidth = (gridCellMinWidth * scale).coerceIn(60f, 420f)
                                        }
                                    }
                                    previousSpan = span
                                    pressed.forEach { it.consume() }
                                } else {
                                    previousSpan = 0f
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleMedia, key = { it.uri.toString() }) { item ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = palette.card),
                            modifier = Modifier.pointerInput(item.uri) {
                                detectTapGestures(
                                    onTap = {
                                        viewerMedia = visibleMedia
                                        selectedViewerIndex = visibleMedia.indexOf(item).takeIf { it >= 0 }
                                    },
                                    onLongPress = {
                                        if (!item.isVideo) {
                                            zoomedMainPhoto = item
                                            mainPreviewHeld = true
                                        }
                                    },
                                    onPress = {
                                        tryAwaitRelease()
                                        if (zoomedMainPhoto?.uri == item.uri) {
                                            mainPreviewHeld = false
                                            delay(240)
                                            if (!mainPreviewHeld && zoomedMainPhoto?.uri == item.uri) {
                                                zoomedMainPhoto = null
                                            }
                                        }
                                    }
                                )
                            },
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            ) {
                                if (item.isVideo) {
                                    VideoThumbnail(item = item, palette = palette)
                                } else {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = item.name,
                                        modifier = Modifier.fillMaxSize().background(palette.surface),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                if (gridColumnCount <= 2) {
                                    // Text sits directly over the photo: no strip, panel or opaque background.
                                    val nameSize = if (maxWidth < 120.dp) 10.sp else 12.sp
                                    val dateSize = if (maxWidth < 120.dp) 9.sp else 10.sp
                                    val photoCaptionStyle = TextStyle(
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.96f),
                                            offset = Offset(1.2f, 1.2f),
                                            blurRadius = 3.5f
                                        )
                                    )
                                    Column(
                                        modifier = Modifier.align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 3.dp),
                                        verticalArrangement = Arrangement.spacedBy(0.dp)
                                    ) {
                                        Text(
                                            item.name,
                                            maxLines = 1,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            fontSize = nameSize,
                                            lineHeight = nameSize,
                                            style = photoCaptionStyle,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            formatDate(item.dateSeconds),
                                            color = Color.White,
                                            fontSize = dateSize,
                                            lineHeight = dateSize,
                                            style = photoCaptionStyle,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
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
            listOf("⌂  Главная", "✨  ИИ-альбомы").forEachIndexed { index, label ->
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                         .background(if (selectedPage == index) palette.primary else Color.Transparent)
                        .clickable { scope.launch { pagePagerState.animateScrollToPage(index) } }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (selectedPage == index) Color.White else palette.muted, fontSize = 12.sp, fontWeight = if (selectedPage == index) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
    }
    }

    // Match the album photo preview: a centered, screen-sized overlay that
    // grows while the finger is held, without scaling or moving the grid cell.
    if (selectedPage == 0) {
        zoomedMainPhoto?.let { photo ->
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.name,
                    modifier = Modifier.fillMaxWidth(0.62f + 0.37f * mainPreviewProgress)
                        // The main overlay spans the whole screen; the AI page preview
                        // is constrained to its shorter content area. Match that visual size.
                        .height(maxHeight * (0.38f + 0.30f * mainPreviewProgress)),
                    contentScale = ContentScale.Fit
                )
            }
        }
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
                    (context as? Activity)?.finishAndRemoveTask()
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
                        updateDownloadedBytes = 0L
                        updateTotalBytes = 0L
                        try {
                            val started = AppUpdater.downloadAndInstall(context, update) { downloaded, total ->
                                updateDownloadedBytes = downloaded
                                updateTotalBytes = total
                            }
                            if (started) { updateInfo = null; showUpdateDialog = false }
                            else {
                                showUpdateDialog = false
                                updateMessage = "Разреши установку приложений для ФотоПорядка в настройках. Вернись сюда и снова нажми «Скачать и установить»."
                            }
                        } catch (error: Exception) {
                            // Show the failure above the update dialog instead of hiding it behind that dialog.
                            showUpdateDialog = false
                            updateMessage = "Не удалось скачать или открыть обновление: ${error.message ?: error.javaClass.simpleName}. Попробуй ещё раз."
                        } finally { downloadingUpdate = false }
                    }
                }) { Text(if (downloadingUpdate) "Скачиваем…" else "Скачать и установить") }
            },
            dismissButton = { TextButton(onClick = { showUpdateDialog = false }, enabled = !downloadingUpdate) { Text("Позже") } },
            title = { Text("Доступна версия ${update.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text((update.notes.substringBefore("\n\n").ifBlank { "Доступна новая версия приложения." }) + "\n\nФайл: app-release.apk\nИсточник: GitHub → Releases → ФотоПорядок " + update.versionName)
                    if (downloadingUpdate) {
                        if (updateTotalBytes > 0L) {
                            val percent = ((updateDownloadedBytes * 100L) / updateTotalBytes).toInt().coerceIn(0, 100)
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { updateDownloadedBytes.toFloat() / updateTotalBytes.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Скачано: $percent%  •  ${updateDownloadedBytes / (1024 * 1024)} из ${updateTotalBytes / (1024 * 1024)} МБ")
                        } else {
                            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Подключаемся к серверу загрузки…")
                        }
                    }
                }
            }
        )
    }

    selectedViewerIndex?.let { index ->
        FullscreenMediaViewer(
            media = viewerMedia,
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
    val context = LocalContext.current
    val activity = context as? Activity
    val initialRequestedOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    var isLandscape by remember {
        mutableStateOf(context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    }
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = initialRequestedOrientation }
    }
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
            var playerControlsVisible by remember(item.uri) { mutableStateOf(false) }
            if (item.isVideo) {
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
                            setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility: Int ->
                                    playerControlsVisible = visibility == View.VISIBLE
                                }
                            )
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
            if (item.isVideo && playerControlsVisible) {
                IconButton(
                    onClick = {
                        isLandscape = !isLandscape
                        activity?.requestedOrientation = if (isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd)
                        // The fullscreen dialog draws edge-to-edge. Respect safe drawing
                        // insets (including the side inset in landscape) before placing
                        // the orientation control so it never sits beyond the display.
                        .navigationBarsPadding()
                        .padding(end = 12.dp, bottom = 76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC202020))
                ) {
                    Text(
                        "⛶",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
    val shape = SlantedButtonShape(0.10f)
    Box(
        modifier = Modifier.fillMaxWidth()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(palette.secondary, palette.primary, palette.primary.copy(alpha = 0.82f))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.88f), palette.primary.copy(alpha = 0.35f))),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = palette.onPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Box(Modifier.fillMaxWidth(0.58f).height(1.dp).background(Color.White.copy(alpha = 0.52f)))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, accent: Color, palette: AppTheme) {
    val shape = RoundedCornerShape(17.dp)
    Card(
        modifier = modifier.shadow(10.dp, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp, pressedElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(palette.card.copy(alpha = 0.98f), palette.surface, palette.card)))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.92f), accent.copy(alpha = 0.24f))), shape)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(label, color = palette.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(value, color = palette.text, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun SortButton(label: String, selected: Boolean, palette: AppTheme, onClick: () -> Unit) {
    val shape = SlantedButtonShape(0.14f)
    val fill = if (selected) {
        Brush.verticalGradient(listOf(palette.secondary, palette.primary, palette.primary.copy(alpha = 0.86f)))
    } else {
        Brush.verticalGradient(listOf(palette.card, palette.surface, palette.card.copy(alpha = 0.92f)))
    }
    Box(
        modifier = Modifier.height(36.dp)
            .shadow(if (selected) 8.dp else 4.dp, shape)
            .clip(shape)
            .background(fill)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (selected) 0.82f else 0.92f), palette.muted.copy(alpha = 0.20f))
                ),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            color = if (selected) palette.onPrimary else palette.text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
        )
    }
}



private const val APP_NOTIFICATION_CHANNEL = "photoorder_updates"

private fun systemNotificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun appNotificationsEnabled(context: Context): Boolean =
    context.getSharedPreferences("photoorder_prefs", Context.MODE_PRIVATE)
        .getBoolean("notifications_enabled", true) && systemNotificationsAllowed(context)

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

private fun postAppNotification(context: Context, title: String, message: String, notificationId: Int, update: UpdateInfo? = null) {
    if (!appNotificationsEnabled(context)) return
    update?.let { saveNotificationUpdate(context, it) }
    createAppNotificationChannel(context)
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_OPEN_UPDATE_DIALOG, true)
        update?.let {
            putExtra(EXTRA_UPDATE_VERSION_CODE, it.versionCode)
            putExtra(EXTRA_UPDATE_VERSION_NAME, it.versionName)
            putExtra(EXTRA_UPDATE_DOWNLOAD_URL, it.downloadUrl)
            putExtra(EXTRA_UPDATE_NOTES, it.notes)
        }
    }
    val pendingIntent = PendingIntent.getActivity(
        context, notificationId, launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
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

@Composable
private fun VideoThumbnail(item: MediaItem, palette: AppTheme) {
    val context = LocalContext.current
    val frame by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = item.uri
    ) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, item.uri)
                retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(palette.surface)
    ) {
        // Play marks scale with the actual tile width, so dense 4–5 column
        // layouts keep the video indicator compact instead of covering the frame.
        val centerPlaySize = (maxWidth.value * 0.22f).coerceIn(9f, 18f).sp
        val badgePlaySize = (maxWidth.value * 0.14f).coerceIn(7f, 11f).sp
        val badgeInset = (maxWidth.value * 0.09f).coerceIn(3f, 8f).dp
        if (frame != null) {
            AsyncImage(
                model = frame,
                contentDescription = "Кадр из видео",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("▶", color = palette.accent, fontSize = centerPlaySize, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(badgeInset)
                .clip(RoundedCornerShape((badgeInset.value).dp)).background(Color(0xAA000000))
                .padding(horizontal = badgeInset, vertical = badgeInset * 0.5f)
        ) { Text("▶", color = Color.White, fontSize = badgePlaySize, lineHeight = badgePlaySize) }
    }
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
