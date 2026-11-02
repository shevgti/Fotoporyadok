package ru.shev.photoorder

import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaItem(
    val uri: Uri,
    val date: Long,
    val name: String,
    val isVideo: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            permissionGranted.value = true
            loadMedia()
        } else {
            permissionGranted.value = hasMediaPermission()
        }
    }

    private val media = mutableStateListOf<MediaItem>()
    private val permissionGranted = mutableStateOf(false)
    private val loading = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                LaunchedEffect(Unit) {
                    if (hasMediaPermission()) {
                        permissionGranted.value = true
                        loadMedia()
                    } else {
                        requestMediaPermission()
                    }
                }

                val visibleMedia = when (selectedTab) {
                    1 -> media.filter { !it.isVideo }
                    2 -> media.filter { it.isVideo }
                    else -> media
                }

                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("ФотоПорядок") })
                    }
                ) { padding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            "Фото и видео на телефоне",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Все оригиналы остаются на устройстве. " +
                                "Приложение пока только показывает и учитывает медиафайлы."
                        )
                        Spacer(Modifier.height(12.dp))

                        if (loading.value) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }

                        Text(
                            if (!permissionGranted.value) {
                                "Нужно разрешение на доступ к фото и видео."
                            } else {
                                "Всего: ${media.size} · Фото: ${media.count { !it.isVideo }} · " +
                                    "Видео: ${media.count { it.isVideo }}"
                            }
                        )

                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Всё") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Фото") }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Видео") }
                            )
                        }

                        if (visibleMedia.isEmpty() && permissionGranted.value && !loading.value) {
                            Text(
                                "В этой категории пока ничего нет.",
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(112.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visibleMedia, key = { it.uri.toString() }) { item ->
                                Column {
                                    if (item.isVideo) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(112.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Box(
                                                contentAlignment = androidx.compose.ui.Alignment.Center
                                            ) {
                                                Text(
                                                    "▶\nВИДЕО",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                        }
                                    } else {
                                        AsyncImage(
                                            model = item.uri,
                                            contentDescription = item.name,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(112.dp)
                                        )
                                    }
                                    Text(
                                        item.name,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        SimpleDateFormat(
                                            "dd.MM.yyyy",
                                            Locale.getDefault()
                                        ).format(Date(item.date)),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestMediaPermission() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions.launch(permissions)
    }

    private fun hasMediaPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 34) {
            return checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun loadMedia() {
        loading.value = true
        media.clear()

        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )
        val selection =
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR " +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val isVideo =
                    cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val baseUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                media.add(
                    MediaItem(
                        uri = ContentUris.withAppendedId(baseUri, id),
                        date = cursor.getLong(dateColumn) * 1000L,
                        name = cursor.getString(nameColumn) ?: if (isVideo) "Видео" else "Фото",
                        isVideo = isVideo
                    )
                )
            }
        }

        permissionGranted.value = hasMediaPermission()
        loading.value = false
    }
}
