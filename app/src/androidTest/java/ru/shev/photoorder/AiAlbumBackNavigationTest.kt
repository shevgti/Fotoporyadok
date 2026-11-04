package ru.shev.photoorder

import android.Manifest
import android.content.ContentValues
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.Color
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.MediaStore
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end regression tests for the real MainActivity + AiOrganizerPage BackHandler.
 * The test seeds three local MediaStore photos and their AI cache rows so no ML/network
 * inference is needed; navigation is exercised through Android's system Back dispatcher.
 */
@RunWith(AndroidJUnit4::class)
class AiAlbumBackNavigationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var device: UiDevice
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand("settings put secure navigation_mode 2")
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.READ_MEDIA_IMAGES
        )
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        seedCachedAlbum()
        assertFixtureVisibleToApp()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("ФотоПорядок")
        // The label exists before the asynchronous MediaStore refresh finishes.\n        // Do not enter the AI screen until all three fixture images are in the UI state.\n        waitForTextContains("Показано: 3")
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("fp_back_test_%")
        )
        val databaseFile = context.getDatabasePath("ai_photo_labels.db")
        if (databaseFile.exists()) {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { it.delete("labels", null, null) }
        }
    }

    @Test
    fun visibleBackButtonReturnsFromAlbumToAlbumList() {
        openAiAlbum()

        waitForText("Назад").click()

        waitForTextContains("Умные альбомы")
        assertNotNull(device.findObject(By.textContains("Отдых на море")))
    }

    @Test
    fun systemBackUnwindsAlbumAndReturnsHomeBeforeExitConfirmation() {
        openAiAlbum()

        device.pressBack()
        waitForTextContains("Умные альбомы")
        assertNotNull(device.findObject(By.textContains("Отдых на море")))

        device.pressBack()
        waitForTextContains("Показано:")

        device.pressBack()
        waitForText("Выйти из ФотоПорядка?")
        waitForText("Отмена").click()
        waitForTextContains("Показано:")
    }

    @Test
    fun systemBackFromCategoryReturnsToAiAlbumList() {
        waitForTextContains("ИИ-альбомы").click()
        waitForText("ИИ-фотограф")
        runAiAnalysisAndWait()
        scrollToText("Природа").click()
        waitForText("Назад")

        device.pressBack()
        waitForTextContains("Умные альбомы")
        device.pressBack()
        waitForTextContains("Показано:")
    }

    @Test
    fun edgeSwipeReturnsFromAlbumInsteadOfFinishingActivity() {
        openAiAlbum()

        val height = device.displayHeight
        val width = device.displayWidth
        assertTrue(
            "The left-edge Back gesture should be recognized",
            device.swipe(1, height / 2, width / 3, height / 2, 24)
        )

        waitForTextContains("Умные альбомы")
        assertNotNull(device.findObject(By.textContains("Отдых на море")))
    }

    private fun openAiAlbum() {
        waitForTextContains("ИИ-альбомы").click()
        waitForText("ИИ-фотограф")
        runAiAnalysisAndWait()
        waitForTextContains("Отдых на море").click()
        waitForText("Назад")
    }

    private fun runAiAnalysisAndWait() {
        // AiOrganizerPage can load its cache automatically. If it has not
        // appeared yet, start analysis explicitly; never assume the button
        // still has its initial label after LaunchedEffect(media) completes.
        val cachedAlbums = device.wait(
            Until.findObject(By.textContains("Умные альбомы")),
            5_000
        )
        if (cachedAlbums == null) {
            waitForTextContains("Запустить ИИ-анализ").click()
            waitForTextContains("Готово: распознано 3 фото")
        }
        waitForTextContains("Умные альбомы")
        waitForTextContains("Отдых на море")
    }

    private fun scrollToText(text: String): UiObject2 {
        repeat(5) {
            val found = device.findObject(By.text(text))
            if (found != null) return found
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 3,
                12
            )
        }
        return waitForText(text)
    }

    private fun waitForText(text: String): UiObject2 {
        val found = device.wait(Until.findObject(By.text(text)), 20_000)
        assertNotNull("Timed out waiting for exact text: $text", found)
        return found!!
    }

    private fun waitForTextContains(text: String): UiObject2 {
        val found = device.wait(Until.findObject(By.textContains(text)), 20_000)
        assertNotNull("Timed out waiting for text containing: $text", found)
        return found!!
    }

    private fun assertFixtureVisibleToApp() {
        assertEquals(
            "The app must have full image permission on the emulator",
            PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
        )

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )
        val imageSelection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val imageArgs = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), "fp_back_test_%")
        val mediaCount = resolverCount(
            MediaStore.Files.getContentUri("external"),
            projection,
            imageSelection,
            imageArgs
        )
        assertEquals("MainActivity's MediaStore.Files query should see the 3 fixture photos", 3, mediaCount)

        val dbFile = context.getDatabasePath("ai_photo_labels.db")
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM labels", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("All 3 AI cache rows must exist before MainActivity starts", 3, cursor.getInt(0))
            }
        }
    }


    private fun seedCacheForVisibleFixture() {
        // Re-query only after MainActivity's MediaStore refresh. This makes
        // each cache signature identical to the MediaItem date/name snapshot
        // already held by the app when AiOrganizerPage is opened.
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_TAKEN
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            "fp_back_test_%"
        )
        val databaseFile = context.getDatabasePath("ai_photo_labels.db")
        SQLiteDatabase.openOrCreateDatabase(databaseFile.absolutePath, null).use { db ->
            db.delete("labels", null, null)
            var seeded = 0
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                args,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val takenColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn) ?: "Фото"
                    val taken = if (takenColumn >= 0) cursor.getLong(takenColumn) else 0L
                    val dateSeconds = if (taken > 0L) taken / 1000L else cursor.getLong(addedColumn)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idColumn)
                    )
                    val cached = ContentValues().apply {
                        put("uri", uri.toString())
                        put("signature", "$dateSeconds|$name")
                        put("labels", "beach|sea")
                        put("category", "Природа")
                    }
                    db.insertWithOnConflict(
                        "labels",
                        null,
                        cached,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                    seeded++
                }
            } ?: error("Could not query refreshed MediaStore fixture")
            assertEquals("All 3 refreshed fixture images must have matching AI cache rows", 3, seeded)
        }
    }

    private fun resolverCount(
        uri: android.net.Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>
    ): Int {
        return context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use {
            it.count
        } ?: 0
    }

    private fun seedCachedAlbum() {
        val resolver = context.contentResolver
        resolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("fp_back_test_%")
        )

        val databaseFile = context.getDatabasePath("ai_photo_labels.db")
        databaseFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(databaseFile.absolutePath, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS labels (" +
                    "uri TEXT PRIMARY KEY, signature TEXT NOT NULL, " +
                    "labels TEXT NOT NULL, category TEXT NOT NULL)"
            )
            // Match AiLabelCache's SQLiteOpenHelper schema version so its onCreate()
            // does not try to recreate the table when the app opens the database.
            db.version = 1
            db.delete("labels", null, null)

            val baseTime = System.currentTimeMillis() - 30 * 60 * 1000L
            repeat(3) { index ->
                val name = "fp_back_test_$index.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/FotoporyadokUiTest"
                    )
                    put(MediaStore.Images.Media.DATE_TAKEN, baseTime + index * 2 * 60 * 1000L)
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create test image in MediaStore")
                resolver.openOutputStream(uri).use { stream ->
                    val output = checkNotNull(stream)
                    val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.BLUE)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                    bitmap.recycle()
                }
            }

            // Use the same Files collection, projection and timestamp fallback as MainActivity.
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_TAKEN
            )
            val imageSelection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val imageArgs = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), "fp_back_test_%")
            resolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                imageSelection,
                imageArgs,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn)
                    val taken = cursor.getLong(takenColumn)
                    val dateSeconds = if (taken > 0L) taken / 1000L else cursor.getLong(addedColumn)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idColumn)
                    )
                    val cached = ContentValues().apply {
                        put("uri", uri.toString())
                        put("signature", "$dateSeconds|$name")
                        put("labels", "beach|sea")
                        put("category", "Природа")
                    }
                    db.insertWithOnConflict(
                        "labels",
                        null,
                        cached,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            } ?: error("Could not query test images from MediaStore")
        }
    }
}
