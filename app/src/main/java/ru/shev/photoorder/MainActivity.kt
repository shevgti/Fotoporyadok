package ru.shev.photoorder

import android.Manifest
import android.content.ContentUris
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PhotoItem(
    val uri: Uri,
    val name: String,
    val dateSeconds: Long
)

private enum class PhotoSort { NEWEST, OLDEST, NAME }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhotoOrderScreen()
                }
            }
        }
    }
}

@Composable
private fun PhotoOrderScreen() {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(hasPhotoPermission(context)) }
    var loading by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(PhotoSort.NEWEST) }
    var selectedPhoto by remember { mutableStateOf<PhotoItem?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasPhotoPermission(context)
    }

    LaunchedEffect(hasPermission, refreshKey) {
        if (hasPermission) {
            loading = true
            photos = withContext(Dispatchers.IO) { loadPhotos(context) }
            loading = false
        }
    }

    val visiblePhotos = remember(photos, search, sort) {
        val filtered = photos.filter {
            it.name.contains(search.trim(), ignoreCase = true)
        }
        when (sort) {
            PhotoSort.NEWEST -> filtered.sortedByDescending { it.dateSeconds }
            PhotoSort.OLDEST -> filtered.sortedBy { it.dateSeconds }
            PhotoSort.NAME -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F4F0))
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ФотоПорядок", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { refreshKey++ }, enabled = hasPermission && !loading) {
                Text("Обновить")
            }
        }
        Text(
            "Все фотографии телефона — в удобном порядке",
            color = Color(0xFF66615B),
            modifier = Modifier.padding(bottom = 14.dp)
        )

        if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Разрешите доступ к фотографиям", fontWeight = FontWeight.SemiBold)
                    Text("ФотоПорядок показывает изображения, которые уже хранятся на телефоне. Файлы не отправляются в интернет и не удаляются.")
                    Button(onClick = { permissionLauncher.launch(requiredPhotoPermissions()) }) {
                        Text("Выбрать фотографии")
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Поиск по названию") }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SortButton("Сначала новые", sort == PhotoSort.NEWEST) { sort = PhotoSort.NEWEST }
                SortButton("Сначала старые", sort == PhotoSort.OLDEST) { sort = PhotoSort.OLDEST }
                SortButton("По имени", sort == PhotoSort.NAME) { sort = PhotoSort.NAME }
            }
            Text(
                when {
                    loading -> "Загружаем фотографии…"
                    search.isNotBlank() -> "Найдено: ${visiblePhotos.size} из ${photos.size}"
                    else -> "Фотографий: ${photos.size}"
                },
                color = Color(0xFF5F5A54),
                modifier = Modifier.padding(bottom = 10.dp)
            )

            if (loading && photos.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (visiblePhotos.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (search.isNotBlank()) "Ничего не найдено. Попробуйте другое название."
                        else "На телефоне пока нет доступных фотографий."
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visiblePhotos, key = { it.uri.toString() }) { photo ->
                        Card(
                            modifier = Modifier.clickable { selectedPhoto = photo },
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .background(Color(0xFFE8E4DE)),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.padding(9.dp)) {
                                    Text(
                                        photo.name,
                                        maxLines = 1,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        formatDate(photo.dateSeconds),
                                        color = Color(0xFF77716A),
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

    selectedPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { selectedPhoto = null },
            confirmButton = {
                TextButton(onClick = { selectedPhoto = null }) { Text("Закрыть") }
            },
            title = { Text(photo.name, maxLines = 1) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.name,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Text("Дата: ${formatDate(photo.dateSeconds)}")
                }
            }
        )
    }
}

@Composable
private fun SortButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = if (selected) "✓ $label" else label,
            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF625D56),
            fontSize = 12.sp
        )
    }
}

private fun requiredPhotoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasPhotoPermission(context: Context): Boolean {
    return requiredPhotoPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun loadPhotos(context: Context): List<PhotoItem> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_TAKEN
    )
    val result = mutableListOf<PhotoItem>()
    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn) ?: "Фото"
            val added = cursor.getLong(addedColumn)
            val takenMillis = cursor.getLong(takenColumn)
            val dateSeconds = if (takenMillis > 0) takenMillis / 1000 else added
            result += PhotoItem(
                uri = ContentUris.withAppendedId(collection, id),
                name = name,
                dateSeconds = dateSeconds
            )
        }
    }
    return result
}

private fun formatDate(seconds: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(seconds * 1000))
