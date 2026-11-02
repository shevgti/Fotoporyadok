package ru.shev.photoorder

import android.Manifest
import android.content.ContentUris
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PhotoItem(
    val uri: Uri,
    val name: String,
    val dateAdded: Long
)

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

@androidx.compose.runtime.Composable
private fun PhotoOrderScreen() {
    val context = LocalContext.current
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(hasPhotoPermission(context)) }
    var loading by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasPhotoPermission(context)
        if (hasPermission) {
            loading = true
            photos = loadPhotos(context)
            loading = false
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loading = true
            photos = loadPhotos(context)
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F4F0))
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "ФотоПорядок",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 22.dp)
        )
        Text(
            text = "Ваши фотографии — по датам, в одном месте",
            color = Color(0xFF66615B),
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
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
                    Text("Нужен доступ к фотографиям", fontWeight = FontWeight.SemiBold)
                    Text("Приложение только показывает изображения на телефоне. Оно ничего не загружает в интернет и не удаляет.")
                    Button(onClick = {
                        permissionLauncher.launch(requiredPhotoPermissions())
                    }) {
                        Text("Разрешить доступ")
                    }
                }
            }
        } else {
            Text(
                text = when {
                    loading -> "Загружаем фотографии…"
                    photos.isEmpty() -> "Фотографии не найдены"
                    else -> "Найдено фотографий: ${photos.size}"
                },
                color = Color(0xFF5F5A54),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (photos.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(photos, key = { it.uri.toString() }) { photo ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = photo.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFE8E4DE)),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.padding(9.dp)) {
                                    Text(
                                        text = photo.name,
                                        maxLines = 1,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = formatDate(photo.dateAdded),
                                        color = Color(0xFF77716A),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (!loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 28.dp), contentAlignment = Alignment.Center) {
                    Text("Когда на телефоне появятся фотографии, они отобразятся здесь.")
                }
            }
        }
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

private fun hasPhotoPermission(context: android.content.Context): Boolean {
    val permissions = requiredPhotoPermissions()
    return permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun loadPhotos(context: android.content.Context): List<PhotoItem> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED
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
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn) ?: "Фото"
            val date = cursor.getLong(dateColumn)
            result += PhotoItem(
                uri = ContentUris.withAppendedId(collection, id),
                name = name,
                dateAdded = date
            )
        }
    }
    return result
}

private fun formatDate(seconds: Long): String {
    return SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(seconds * 1000))
}
