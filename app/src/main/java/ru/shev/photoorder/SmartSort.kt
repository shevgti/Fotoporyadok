package ru.shev.photoorder

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class SmartSortFolder(val id: String, val name: String, val items: List<MediaItem>)

internal fun buildSmartSortPlan(media: List<MediaItem>): List<SmartSortFolder> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    return media.groupBy { fmt.format(Date(it.dateSeconds * 1000)) + "|" + it.albumId }
        .map { entry ->
            val day = entry.key.substringBefore("|")
            val album = entry.value.firstOrNull()?.albumName?.takeIf { it.isNotBlank() && it != "Без альбома" } ?: ""
            val date = try {
                SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(fmt.parse(day)!!)
            } catch (_: Exception) { day }
            val name = if (album.isBlank()) date else date + " — " + album
            SmartSortFolder(entry.key, name, entry.value)
        }.sortedByDescending { it.items.maxOfOrNull { m -> m.dateSeconds } ?: 0L }
}

@RequiresApi(Build.VERSION_CODES.R)
internal fun moveSmartSortFolders(context: Context, folders: List<SmartSortFolder>): Pair<Int, Int> {
    var moved = 0
    var failed = 0
    folders.forEach { folder ->
        val safeName = folder.name.replace("/", " ").replace("\\", " ").trim().take(80).ifBlank { "Без названия" }
        folder.items.forEach { item ->
            try {
                val values = ContentValues().apply {
                    val root = if (item.isVideo) "Movies/" else "Pictures/"
                    put(MediaStore.MediaColumns.RELATIVE_PATH, root + "ФотоПорядок/" + safeName + "/")
                }
                if (context.contentResolver.update(item.uri, values, null, null) > 0) moved++ else failed++
            } catch (_: Exception) { failed++ }
        }
    }
    return moved to failed
}

@Composable
internal fun SmartSortPage(
    media: List<MediaItem>,
    folders: List<SmartSortFolder>,
    onAnalyze: () -> Unit,
    onRename: (String, String) -> Unit,
    onConfirm: () -> Unit,
    status: String?,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf<SmartSortFolder?>(null) }
    var renaming by remember { mutableStateOf<SmartSortFolder?>(null) }
    var newName by remember { mutableStateOf("") }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Умная сортировка", fontSize = 22.sp, color = Color.White)
        Text("Предлагаем папки по дате и исходному альбому. Проверь содержимое и названия перед переносом.",
            color = Color(0xFFAAA6C7), fontSize = 12.sp)
        if (folders.isEmpty()) {
            Text("План ещё не создан. Фото и видео пока не перемещаются.", color = Color.White)
            Button(onClick = onAnalyze, enabled = media.isNotEmpty()) { Text("Создать план сортировки") }
        } else {
            Text("Папок: " + folders.size + " · Файлов: " + folders.sumOf { it.items.size }, color = Color.White)
            LazyVerticalGrid(columns = GridCells.Adaptive(145.dp), modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folders, key = { it.id }) { folder ->
                    Card(Modifier.fillMaxWidth().clickable { selected = folder }) {
                        Column(Modifier.padding(10.dp)) {
                            Text("📁 " + folder.name, color = Color.White)
                            Text(folder.items.size.toString() + " файлов", color = Color(0xFFAAA6C7))
                            TextButton(onClick = { renaming = folder; newName = folder.name }) { Text("Переименовать") }
                            TextButton(onClick = { selected = folder }) { Text("Посмотреть") }
                        }
                    }
                }
            }
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("Подтвердить распределение") }
            Text("До подтверждения оригиналы не перемещаются.", color = Color(0xFFAAA6C7), fontSize = 11.sp)
        }
        status?.let { Text(it, color = Color(0xFFB99AFF)) }
    }
    selected?.let { folder ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(folder.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${folder.items.size} файлов")
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(88.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(folder.items, key = { it.uri.toString() }) { item ->
                            Column {
                                if (item.isVideo) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                        color = Color(0xFF24234D),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                            Text("▶", color = Color.White)
                                        }
                                    }
                                } else {
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = item.name,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Text(item.name, color = Color.White, fontSize = 10.sp, maxLines = 1)
                                Text(
                                    SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(item.dateSeconds * 1000)),
                                    color = Color(0xFFAAA6C7),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Закрыть") } })
    }
    renaming?.let { folder ->
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Название папки") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(folder.id, newName.trim()); renaming = null },
                enabled = newName.isNotBlank()) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Отмена") } })
    }
}
