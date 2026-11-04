package ru.shev.photoorder

import android.Manifest
import android.content.ContentValues
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import android.provider.MediaStore
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
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
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("ФотоПорядок")
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
        waitForText("Природа").click()
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
        waitForTextContains("Умные альбомы")
        waitForTextContains("Отдых на море").click()
        waitForText("Назад")
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

            // Read back MediaStore's actual timestamps and IDs. This mirrors MainActivity's
            // dateSeconds fallback exactly, even if a provider rounds DATE_TAKEN.
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_TAKEN
            )
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("fp_back_test_%"),
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
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
