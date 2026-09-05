package to.hadith.audio

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 29)
class ReadingScreensTest {
    @get:Rule val compose = createComposeRule()
    private val ref = CatalogRef("bukhari", "1")
    private val record = ReadingRecord(ref, FirstHadith, FIRST_HADITH_AUDIO_SOURCE)
    private val book = CatalogBook(1, 7, 1, 7)
    private val fixture = ReadingState(current = record, requestedRef = ref, opened = listOf(record), books = listOf(book, CatalogBook(2, 51, 8, 58)), book = book,
        hadiths = listOf(CatalogHadith("1", book, FirstHadith.isnad, FirstHadith.words)), next = CatalogRef("bukhari", "2"),
        library = ReadingLibrary(recent = listOf(RecentReading(ref, 1, 12f))))

    @Test fun approvedScreensRenderAndReaderControlsDispatchTheirActions() {
        var state by mutableStateOf(fixture)
        var fontScale by mutableFloatStateOf(1f)
        var audio by mutableStateOf(AudioUiState(status = AudioStatus.READY, positionSeconds = 12f, durationSeconds = 48f,
            wordTimings = FirstHadith.words.indices.map { WordTiming(it, it, it * 2f, it * 2f + 2f) }))
        val actions = mutableListOf<ReadingAction>()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                HadithToTheme(state.settings.appearance) { ReadingApp(state, audio) { actions += it } }
            }
        }
        val saved = fixture.library.copy(saved = listOf(ref), words = listOf(SavedWord(ref, FirstHadith.words[2])))
        val scenes = listOf(
            "01-library" to fixture,
            "02-collection" to fixture.copy(page = ReadingPage.COLLECTION),
            "03-hadith-list" to fixture.copy(page = ReadingPage.HADITHS),
            "04-search" to fixture.copy(page = ReadingPage.SEARCH, query = "intentions", exactResult = record),
            "05-reader" to fixture.copy(page = ReadingPage.READER),
            "06-study" to fixture.copy(page = ReadingPage.READER, study = true, meaning = true),
            "07-word" to fixture.copy(page = ReadingPage.READER, wordIndex = 2, sheet = ReaderSheet.WORD),
            "08-focus-night" to fixture.copy(page = ReadingPage.READER, focused = true, library = fixture.library.copy(settings = ReadingSettings(appearance = ReadingAppearance.DARK))),
            "09-player" to fixture.copy(page = ReadingPage.PLAYER),
            "10-playback" to fixture.copy(page = ReadingPage.PLAYER, sheet = ReaderSheet.PLAYBACK),
            "11-saved" to fixture.copy(tab = LibraryTab.SAVED, library = saved, savedRecords = listOf(record)),
            "12-downloads" to fixture.copy(page = ReadingPage.DOWNLOADS, offlineRecords = listOf(record), offlineBytes = 1_500_000,
                downloads = listOf(ReadingDownload(CatalogRef("bukhari", "2"), DownloadStatus.RUNNING, 450_000, 1_200_000))),
            "13-settings" to fixture.copy(page = ReadingPage.SETTINGS),
            "14-appearance" to fixture.copy(page = ReadingPage.APPEARANCE),
            "15-sources" to fixture.copy(page = ReadingPage.SOURCES),
            "16-report" to fixture.copy(page = ReadingPage.REPORT, wordIndex = 2),
            "17-recent" to fixture.copy(tab = LibraryTab.RECENT),
            "18-empty-saved" to fixture.copy(tab = LibraryTab.SAVED),
            "19-offline" to fixture.copy(page = ReadingPage.COLLECTION, online = false, error = "You’re offline. Downloaded passages remain available."),
            "20-loading" to fixture.copy(page = ReadingPage.HADITHS, loading = true),
        )
        scenes.forEach { (name, scene) ->
            compose.runOnIdle { state = scene }
            capture(name)
        }
        compose.runOnIdle { state = fixture.copy(page = ReadingPage.READER) }
        compose.onNodeWithContentDescription("Save this hadith").performClick()
        compose.runOnIdle { assertTrue(actions.contains(ReadingAction.Save(ref))) }
        compose.onNodeWithText("Study", substring = false).performClick()
        compose.runOnIdle { assertTrue(actions.contains(ReadingAction.ToggleStudy)) }
        compose.onNodeWithContentDescription("Play audio").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(actions.contains(ReadingAction.PlayPause)); audio = audio.copy(status = AudioStatus.ERROR, usingTimingPreview = true) }
        compose.onNodeWithContentDescription("Play audio").assertIsNotEnabled()
        compose.onNodeWithText("Retry audio").assertExists()
        capture("21-audio-unavailable")
        compose.runOnIdle { state = fixture.copy(page = ReadingPage.READER); fontScale = 1.6f }
        capture("22-large-type-reader")
        compose.onNodeWithContentDescription("Reader tools").assertIsDisplayed()
        compose.runOnIdle { state = fixture.copy(page = ReadingPage.SETTINGS) }
        capture("23-large-type-settings")
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        // Shared test images survive Gradle uninstalling the test app after the run.
        val resolver = instrumentation.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/hadith-ui")
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        bitmap.recycle()
    }
}
