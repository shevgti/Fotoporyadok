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
import androidx.compose.foundation.lazy.grid.items
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
        "nature" -> "Природа"; "outdoor" -> "На природе"; "scenery" -> "Пейзаж"; "travel" -> "Путешествие"; "barbecue", "grill", "grilling" -> "Шашлыки"; "picnic" -> "Пикник"; "water" -> "Вода"; "waterfall" -> "Водопад"; "flower" -> "Цветы"; "plant" -> "Растения"
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
    val all = labels.map { it.lowercase(Locale.ROOT) }.toSet()
    val animalSignals = all.intersect(setOf("dog", "cat", "pet", "animal", "bird", "horse", "wildlife", "mammal", "reptile", "fish"))
    // People take priority over a stray animal label: a dog in the frame
    // must not turn a group photo into an "Animals" photo.
    return when {
        all.any { it in setOf("screenshot", "document", "text", "poster", "paper") } -> "Документы и скриншоты"
        all.any { it in setOf("person", "people", "human", "face", "portrait", "selfie", "crowd", "group") } -> "Люди"
        animalSignals.size >= 2 -> "Животные"
        all.any { it in setOf("car", "vehicle", "automobile", "truck", "motorcycle", "bicycle", "airplane", "train", "boat") } -> "Транспорт"
        all.any { it in setOf("food", "dish", "cuisine", "fruit", "vegetable", "dessert", "cake", "meal", "drink", "barbecue", "grill", "picnic") } -> "Еда"
        all.any { it in setOf("building", "architecture", "house", "church", "tower", "bridge", "city", "skyscraper", "monument") } -> "Город и архитектура"
        all.any { it in setOf("travel", "tourism", "vacation", "sightseeing", "landmark") } -> "Путешествия"
        all.any { it in setOf("beach", "ocean", "sea", "lake", "mountain", "landscape", "forest", "tree", "waterfall", "flower", "plant", "nature", "outdoor", "park") } -> "Природа"
        else -> "Разное"
    }
}

private val eventContextLabels = setOf(
    "outdoor", "park", "grass", "tree", "forest", "nature", "landscape", "camping",
    "picnic", "barbecue", "grill", "grilling", "food", "meal", "people", "person",
    "crowd", "group", "dog", "pet", "family", "friend"
)

private fun eventTitleFor(group: List<AiPhotoResult>): String {
    val labels = group.flatMap { it.labels }.map { it.lowercase(Locale.ROOT) }.toSet()
    val hasPeople = labels.any { it in setOf("person", "people", "human", "face", "portrait", "selfie", "crowd", "group", "family", "friend") }
    val hasOutdoor = labels.any { it in setOf("outdoor", "park", "grass", "tree", "forest", "nature", "landscape", "camping") }
    val hasFood = labels.any { it in setOf("food", "meal", "dish", "cuisine", "barbecue", "grill", "grilling", "picnic", "cake", "drink") }
    return when {
        labels.any { it in setOf("barbecue", "grill", "grilling") } -> "Шашлыки и отдых"
        labels.contains("picnic") -> "Пикник на природе"
        hasPeople && hasOutdoor -> "Встреча на природе"
        hasPeople -> "Встреча с друзьями"
        hasFood -> "Угощение и застолье"
        hasOutdoor -> "Прогулка на природе"
        else -> group.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key
            ?.takeIf { it !in setOf("Разное", "Не распознано", "Животные", "Природа и путешествия") }
            ?: "Фотособытие"
    }
}

internal fun buildAiEventAlbums(results: List<AiPhotoResult>): List<AiEventAlbum> {
    if (results.isEmpty()) return emptyList()
    val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
    val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    val groups = mutableListOf<MutableList<AiPhotoResult>>()

    // Event grouping is based primarily on a same-day shooting session.
    // Labels provide a secondary scene/context bridge, but category changes
    // (people -> dog -> food -> park) no longer split one real-life event.
    results.sortedBy { it.media.dateSeconds }.forEach { photo ->
        val current = groups.lastOrNull()
        val previous = current?.lastOrNull()
        if (previous == null) {
            groups.add(mutableListOf(photo))
        } else {
            val sameDay = dayFormat.format(Date(previous.media.dateSeconds * 1000)) ==
                dayFormat.format(Date(photo.media.dateSeconds * 1000))
            val gapSeconds = (photo.media.dateSeconds - previous.media.dateSeconds).coerceAtLeast(0L)
            val currentContext = current.flatMap { it.labels }
                .map { it.lowercase(Locale.ROOT) }.toSet().intersect(eventContextLabels)
            val photoContext = photo.labels.map { it.lowercase(Locale.ROOT) }.toSet()
                .intersect(eventContextLabels)
            val sharedSceneContext = currentContext.intersect(photoContext).isNotEmpty()
            val belongsToSameSession = gapSeconds <= 4 * 60 * 60L ||
                (gapSeconds <= 8 * 60 * 60L && sharedSceneContext)

            if (sameDay && belongsToSameSession) current.add(photo)
            else groups.add(mutableListOf(photo))
        }
    }

    return groups.filter { it.size >= 3 }.map { group ->
        val date = dateFormat.format(Date(group.first().media.dateSeconds * 1000))
        val title = eventTitleFor(group)
        AiEventAlbum(group.first().media.uri.toString(), "$title · $date", date, group)
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
            val result = if (cached != null) AiPhotoResult(item, cached.labels, categoryForLabels(cached.labels)) else {
                try {
                    val image = InputImage.fromFilePath(context, item.uri)
                    val labels = Tasks.await(labeler.process(image)).filter { it.confidence >= 0.62f }
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

private fun loadCachedAiResults(context: Context, media: List<MediaItem>): List<AiPhotoResult> {
    val cache = AiLabelCache(context)
    return try {
        media.asSequence().filterNot { it.isVideo }.mapNotNull { item ->
            val signature = item.dateSeconds.toString() + "|" + item.name
            cache.get(item.uri.toString(), signature)?.let { AiPhotoResult(item, it.labels, categoryForLabels(it.labels)) }
        }.toList()
    } finally {
        cache.close()
    }
}

@Composable
internal fun AiOrganizerPage(
    media: List<MediaItem>,
    palette: AppTheme,
    modifier: Modifier = Modifier,
    onExit: () -> Unit
) {
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

    LaunchedEffect(media) {
        if (media.isNotEmpty() && results.isEmpty()) {
            val cached = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                loadCachedAiResults(context, media)
            }
            if (cached.isNotEmpty()) {
                results = cached
                albums = buildAiEventAlbums(cached)
                status = "Загружены сохранённые результаты анализа: ${cached.size} фото, альбомов ${albums.size}."
            }
        }
    }

    // Own every Back gesture while this page is visible. First close the
    // nested album/category; only a second Back returns to the main screen.
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            selectedAlbum != null -> selectedAlbum = null
            selectedCategory != null -> selectedCategory = null
            else -> onExit()
        }
    }

    // Fill the bounded area provided by the parent. The weighted LazyColumn
    // then scrolls only inside this area and cannot draw over following UI.
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                                modifier = Modifier.fillMaxWidth().height(132.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                userScrollEnabled = false
                            ) {
                                items(album.photos.take(4), key = { it.media.uri.toString() }) { photo ->
                                    AsyncImage(
                                        model = photo.media.uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().height(62.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
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
                Text(
                    selectedCategory!!,
                    modifier = Modifier.weight(1f),
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                TextButton(onClick = { selectedCategory = null }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) { Text("Назад") }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categoryPhotos, key = { it.media.uri.toString() }) { photo ->
                    AsyncImage(
                        model = photo.media.uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        } else if (selectedAlbum != null) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    selectedAlbum!!.title,
                    modifier = Modifier.weight(1f),
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                TextButton(onClick = { selectedAlbum = null }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) { Text("Назад") }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedAlbum!!.photos, key = { it.media.uri.toString() }) { photo ->
                    AsyncImage(
                        model = photo.media.uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        } else if (results.isEmpty() && !analyzing) {
            Text("После анализа здесь появятся альбомы, названные по содержимому фотографий.", color = palette.muted, fontSize = 12.sp)
        }
    }
}
