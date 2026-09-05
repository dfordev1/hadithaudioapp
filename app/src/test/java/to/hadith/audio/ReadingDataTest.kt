package to.hadith.audio

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ReadingDataTest {
    private val ref = CatalogRef("bukhari", "1")
    private val record = ReadingRecord(ref, FirstHadith, FIRST_HADITH_AUDIO_SOURCE)

    @Test fun storagePreservesArabicTranslationsAndExactReportSuffixes() {
        val suffix = CatalogRef("muslim", "1433abc")
        val complex = record.copy(ref = suffix, entry = FirstHadith.copy(english = "A \"quote\"\nA backslash \\ and tab\t.", urdu = "نیتوں کے مطابق"))
        val store = ReadingStore(Files.createTempDirectory("hadith-roundtrip").toFile())
        store.save(complex)
        assertEquals(complex, store.read(suffix))
        assertEquals(complex.entry.arabic, store.read(suffix)?.entry?.arabic)
        assertNull(store.read(CatalogRef("muslim", "1433")))
    }

    @Test fun savedItemsAndReadingChoicesSurviveReopeningTheStore() {
        val root = Files.createTempDirectory("hadith-library").toFile()
        val chosen = ReadingLibrary(settings = ReadingSettings(appearance = ReadingAppearance.DARK, typeface = ArabicTypeface.AMIRI, language = ReadingLanguage.BOTH, speed = 1.5f))
            .toggleSaved(ref).opened(ref, 1234).copy(words = listOf(SavedWord(ref, FirstHadith.words[2])))
        ReadingStore(root).writeLibrary(chosen)
        assertEquals(chosen, ReadingStore(root).readLibrary())
    }

    @Test fun equivalentIdsToggleOneBookmarkAndRecentEntry() {
        val saved = ReadingLibrary().toggleSaved(CatalogRef("bukhari", "0001"))
        assertEquals(listOf(ref), saved.saved)
        assertTrue(saved.toggleSaved(ref).saved.isEmpty())
        val first = ReadingLibrary(recent = listOf(RecentReading(ref, 1, 22.4f)))
        val updated = first.opened(CatalogRef("bukhari", "0001"), 2)
        assertEquals(1, updated.recent.size)
        assertEquals(22.4f, updated.recent.single().position, .0001f)
        assertEquals(2L, updated.recent.single().openedAt)
    }

    @Test fun recentHistoryIsBoundedAndNewestComesFirst() {
        var library = ReadingLibrary()
        (1..110).forEach { library = library.opened(CatalogRef("bukhari", "$it"), it.toLong()) }
        assertEquals(100, library.recent.size)
        assertEquals("110", library.recent.first().ref.normalizedNumber)
        assertEquals("11", library.recent.last().ref.normalizedNumber)
    }

    @Test fun interruptedDownloadsNeverAppearReadyOffline() {
        val store = ReadingStore(Files.createTempDirectory("hadith-download").toFile())
        store.save(record)
        store.partial(ref).writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.offline(ref))
        assertTrue(store.offlineRecords().isEmpty())
        val timing = HadithTiming(FIRST_HADITH_AUDIO_FALLBACK_URL, 1f, listOf(TimingToken("إِنَّمَا", 0f, 1f, gloss = "only", urduGloss = "بے شک")))
        store.finishDownload(ref, timing)
        assertEquals(timing, store.offline(ref)?.timing)
        assertEquals(listOf(record), store.offlineRecords())
        assertFalse(store.partial(ref).exists())
        store.removeDownload(ref)
        assertNull(store.offline(ref))
        assertEquals(record, store.read(ref))
    }

    @Test fun corruptPreferencesFallBackWithoutLosingAbilityToRead() {
        val root = Files.createTempDirectory("hadith-corrupt").toFile()
        root.resolve("library.json").writeText("{truncated")
        assertEquals(ReadingLibrary(), ReadingStore(root).readLibrary())
        val settings = ReadingSettings(arabicSize = Float.NaN, translationSize = 100f, lineSpacing = -1f, speed = Float.POSITIVE_INFINITY).constrained()
        assertEquals(32f, settings.arabicSize, .0001f)
        assertEquals(26f, settings.translationSize, .0001f)
        assertEquals(1.4f, settings.lineSpacing, .0001f)
        assertEquals(1f, settings.speed, .0001f)
    }

    @Test fun persistedRecordsCannotIntroduceAnotherHostOrCollection() {
        val json = ReadingCodec.record(record)
        assertThrows(Exception::class.java) { ReadingCodec.record(json.replace("pub-4c1d62290e264660b4061d58417926be.r2.dev", "example.org")) }
        assertThrows(Exception::class.java) { ReadingCodec.record(json.replace("\"slug\":\"bukhari\"", "\"slug\":\"unknown\"")) }
    }

    @Test fun sidecarWordsStayAuthoritativeAndUnmatchedWordsGetNoInventedMeaning() {
        val state = ReadingState(current = record)
        val words = displayedReadingWords(state, AudioUiState(timedWords = listOf("غيرمطابق", FirstHadith.words[0].arabic)))
        assertEquals("غيرمطابق", words[0].arabic)
        assertEquals("", words[0].gloss)
        assertEquals(FirstHadith.words[0].gloss, words[1].gloss)
        val published = HadithWord("غيرمطابق", "", "Published meaning", null, urduGloss = "معنی")
        val enriched = displayedReadingWords(state, AudioUiState(timedWords = listOf(published.arabic), timedMeanings = listOf(published)))
        assertEquals(published, enriched.single())
    }
}
