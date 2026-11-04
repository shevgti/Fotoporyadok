package ru.shev.photoorder

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

internal data class AiPhotoResult(val media: MediaItem, val labels: List<String>, val category: String)
internal data class AiEventAlbum(val id: String, val title: String, val dateLabel: String, val photos: List<AiPhotoResult>)
private data class CachedAiLabels(val labels: List<String>, val category: String)

private class AiLabelCache(context: Context) : SQLiteOpenHelper(context.applicationContext, "ai_photo_labels.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE labels (uri TEXT PRIMARY KEY, signature TEXT NOT NULL, labels TEXT NOT NULL, category TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun get(uri: String, signature: String): CachedAiLabels? {
        readableDatabase.rawQuery("SELECT labels, category FROM labels WHERE uri=? AND signature=?", arrayOf(uri, signature)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return CachedAiLabels(cursor.getString(0).split('|').filter { it.isNotBlank() }, cursor.getString(1))
        }
    }
    fun put(uri: String, signature: String, labels: List<String>, category: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO labels(uri, signature, labels, category) VALUES(?,?,?,?)",
            arrayOf(uri, signature, labels.joinToString("|"), category))
    }
}

private fun russianLabel(label: String): String {
    return when (label.lowercase(Locale.ROOT)) {
        "beach" -> "Пляж"; "ocean", "sea" -> "Море"; "lake" -> "Озеро"; "mountain" -> "Горы"
        "landscape" -> "Пейзаж"; "sky" -> "Небо"; "forest" -> "Лес"; "tree" -> "Деревья"
        "nature" -> "Природа"; "outdoor" -> "На природе"; "scenery" -> "Пейзаж"; "travel" -> "Путешествие"; "water" -> "Вода"; "waterfall" -> "Водопад"; "flower" -> "Цветы"; "plant" -> "Растения"
        "dog" -> "Собака"; "cat" -> "Кошка"; "pet" -> "Питомец"; "animal" -> "Животное"
        "bird" -> "Птицы"; "horse" -> "Лошадь"; "car", "automobile" -> "Автомобиль"
        "vehicle" -> "Транспорт"; "food" -> "Еда"; "cake" -> "Торт"; "fruit" -> "Фрукты"
        "building" -> "Здание"; "architecture" -> "Архитектура"; "house" -> "Дом"
        "church" -> "Церковь"; "city" -> "Город"; "person", "people", "human" -> "Люди"
        "face", "portrait" -> "Портрет"; "selfie" -> "Селфи"; "document" -> "Документ"
        "text" -> "Текст"; "screenshot" -> "Скриншот"; "bridge" -> "Мост"; "boat" -> "Лодка"
        "bicycle" -> "Велосипед"; "airplane" -> "Самолёт"; "train" -> "Поезд"
        else -> label.replaceFirstChar { it.uppercase() }
    }
}

private fun categoryForLabels(labels: List<String>): String {
    val all = labels.map { it.lowercase(Locale.ROOT) }
    return when {
        all.any { it in setOf("dog", "cat", "pet", "animal", "bird", "horse", "wildlife", "mammal", "reptile", "fish") } -> "Животные"
        all.any { it in setOf("beach", "ocean", "sea", "lake", "mountain", "landscape", "sky", "forest", "tree", "nature", "waterfall", "flower", "plant") } -> "Природа и путешествия"
        all.any { it in setOf("car", "vehicle", "automobile", "truck", "motorcycle", "bicycle", "airplane", "train", "boat") } -> "Транспорт"
        all.any { it in setOf("food", "dish", "cuisine", "fruit", "vegetable", "dessert", "cake", "meal", "drink") } -> "Еда"
        all.any { it in setOf("building", "architecture", "house", "church", "tower", "bridge", "city", "skyscraper", "monument") } -> "Город и архитектура"
        all.any { it in setOf("person", "people", "human", "face", "portrait", "selfie", "crowd") } -> "Люди"
        all.any { it in setOf("document", "text", "screenshot", "poster", "paper") } -> "Документы и скриншоты"
        else -> "Разное"
    }
}

internal fun buildAiEventAlbums(results: List<AiPhotoResult>): List<AiEventAlbum> {
    if (results.isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<AiPhotoResult>>()
    results.sortedBy { it.media.dateSeconds }.forEach { photo ->
        val last = groups.lastOrNull()
        val gap = if (last.isNullOrEmpty()) Long.MAX_VALUE else photo.media.dateSeconds - last.last().media.dateSeconds
        val meaningfulLabels = photo.labels.map { it.lowercase(Locale.ROOT) }.filterNot {
            it in setOf("photo", "photography", "image", "outdoor", "nature", "person", "people")
        }.toSet()
        val sharedSubject = last?.any { previous ->
            previous.labels.any { it.lowercase(Locale.ROOT) in meaningfulLabels }
        } == true
        val sharedCategory = last?.any { previous ->
            previous.category == photo.category && photo.category !in setOf("Разное", "Не распознано")
        } == true
        if (last == null || gap > 3 * 60 * 60 || (!sharedSubject && !sharedCategory)) {
            groups.add(mutableListOf(photo))
        } else {
            last.add(photo)
        }
    }
    val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    return groups.filter { group ->
        if (group.size < 3) return@filter false
        val categoryCounts = group.filter { it.category != "Не распознано" && it.category != "Разное" }.groupingBy { it.category }.eachCount()
        val repeatedLabels = group.flatMap { it.labels }.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
        (categoryCounts.values.maxOrNull() ?: 0) >= 2 || repeatedLabels >= 2
    }.map { group ->
        val topLabels = group.flatMap { it.labels }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(2).map { it.key }
        val category = group.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: "Фотографии"
        val subject = topLabels.takeIf { it.isNotEmpty() }?.joinToString(" и ") { russianLabel(it) } ?: category
        val date = dateFormat.format(Date(group.first().media.dateSeconds * 1000))
        AiEventAlbum(group.first().media.uri.toString(), "$subject · $date", date, group)
    }.sortedByDescending { it.photos.maxOfOrNull { photo -> photo.media.dateSeconds } ?: 0L }
}

internal fun analyzePhotosWithAi(context: Context, media: List<MediaItem>, onProgress: (Int, Int) -> Unit): List<AiPhotoResult> {
    val photos = media.filterNot { it.isVideo }
    if (photos.isEmpty()) return emptyList()
    val cache = AiLabelCache(context)
    val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    val results = mutableListOf<AiPhotoResult>()
    try {
        photos.forEachIndexed { index, item ->
            val uriText = item.uri.toString()
            val signature = item.dateSeconds.toString() + "|" + item.name
            val cached = cache.get(uriText, signature)
            val result = if (cached != null) AiPhotoResult(item, cached.labels, cached.category) else {
                try {
                    val image = InputImage.fromFilePath(context, item.uri)
                    val labels = Tasks.await(labeler.process(image)).filter { it.confidence >= 0.48f }
                        .sortedByDescending { it.confidence }.take(6).map { it.text }
                    val category = categoryForLabels(labels)
                    cache.put(uriText, signature, labels, category)
                    AiPhotoResult(item, labels, category)
                } catch (_: Exception) { AiPhotoResult(item, emptyList(), "Не распознано") }
            }
            results.add(result)
            onProgress(index + 1, photos.size)
        }
    } finally {
        labeler.close()
        cache.close()
    }
    return results
}

@Composable
internal fun AiOrganizerPage(media: List<MediaItem>, palette: AppTheme, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var analyzing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf<List<AiPhotoResult>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AiEventAlbum>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<AiEventAlbum?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ИИ-фотограф", fontSize = 25.sp, color = palette.text, fontWeight = FontWeight.Bold)
        Text("Распознаёт содержимое фотографий прямо на телефоне и собирает большие события в подписанные альбомы. Снимки по одному или по два остаются вне альбомов.",
            color = palette.muted, fontSize = 12.sp)
        if (analyzing) {
            LinearProgressIndicator(progress = { if (total == 0) 0f else progress.toFloat() / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text("Анализ фото: $progress из $total", color = palette.text, fontSize = 12.sp)
        } else {
            Button(onClick = {
                analyzing = true
                progress = 0
                total = media.count { !it.isVideo }
                status = "ИИ изучает фотографии. Это может занять время при первом запуске."
                scope.launch {
                    try {
                        val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            analyzePhotosWithAi(context, media) { done, count ->
                                mainHandler.post { progress = done; total = count }
                            }
                        }
                        results = found
                        albums = buildAiEventAlbums(found)
                        val grouped = albums.sumOf { it.photos.size }
                        val singles = (found.size - grouped).coerceAtLeast(0)
                        status = "Готово: распознано ${found.size} фото, создано альбомов ${albums.size}. Одиночные снимки оставлены без альбомов: $singles."
                    } catch (error: Exception) {
                        status = "Не удалось завершить анализ: " + (error.message ?: "ошибка")
                    } finally { analyzing = false }
                }
            }, enabled = media.any { !it.isVideo } && !analyzing, modifier = Modifier.fillMaxWidth()) {
                Text(if (results.isEmpty()) "✨ Запустить ИИ-анализ" else "↻ Проверить новые фотографии")
            }
        }
        status?.let { Text(it, color = palette.muted, fontSize = 12.sp) }
        if (results.isNotEmpty() && selectedAlbum == null && selectedCategory == null) {
            Text("Умные альбомы · ${albums.size}", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (albums.isEmpty()) Text("Пока не найдено достаточно крупных событий. Для отдельного альбома нужно минимум три фотографии в одной съёмочной серии.", color = palette.muted, fontSize = 12.sp)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(albums, key = { it.id }) { album ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedAlbum = album },
                        colors = CardDefaults.cardColors(containerColor = palette.card), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AsyncImage(model = album.photos.first().media.uri, contentDescription = album.title,
                                modifier = Modifier.fillMaxWidth().height(130.dp), contentScale = ContentScale.Crop)
                            Text(album.title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${album.photos.size} фотографий · ${album.dateLabel}", color = palette.muted, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    Text("Категории фотографий", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                items(
                    results.filter { it.category != "Не распознано" }
                        .groupBy { it.category }.toList().sortedByDescending { it.second.size },
                    key = { it.first }
                ) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedCategory = entry.first },
                        colors = CardDefaults.cardColors(containerColor = palette.card),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.first, color = palette.text, fontWeight = FontWeight.SemiBold)
                            Text("${entry.second.size} фото", color = palette.muted)
                        }
                    }
                }
            }
        } else if (selectedCategory != null) {
            val categoryPhotos = results.filter { it.category == selectedCategory }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(selectedCategory!!, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = { selectedCategory = null }) { Text("Назад") }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categoryPhotos, key = { it.media.uri.toString() }) { photo ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsyncImage(model = photo.media.uri, contentDescription = null, modifier = Modifier.height(86.dp).weight(1f), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text(photo.labels.joinToString(", ").ifBlank { "Нет дополнительных меток" }, color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(photo.media.dateSeconds * 1000)), color = palette.muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        } else if (selectedAlbum != null) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(selectedAlbum!!.title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = { selectedAlbum = null }) { Text("Назад") }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedAlbum!!.photos, key = { it.media.uri.toString() }) { photo ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsyncImage(model = photo.media.uri, contentDescription = null, modifier = Modifier.height(86.dp).weight(1f), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text(photo.category, color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(photo.labels.joinToString(", "), color = palette.muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        } else if (results.isEmpty() && !analyzing) {
            Text("После анализа здесь появятся альбомы, названные по содержимому фотографий.", color = palette.muted, fontSize = 12.sp)
        }
    }
}
