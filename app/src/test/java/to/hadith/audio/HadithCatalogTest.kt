package to.hadith.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HadithCatalogTest {
    @Test fun parsesAllBookTitlesFromTheReaderContract() {
        val rows = (1..97).joinToString(",") { number ->
            """{"book":$number,"count":1,"from":$number,"to":$number}"""
        }
        val books = BukhariCatalogParser.parseIndex("""{"books":[$rows]}""")

        assertEquals("Revelation", books.first().title)
        assertEquals("Oneness of Allah (Tawheed)", books.last().title)
    }

    @Test fun parsesIndexBooksAndReportBookSuffixIds() {
        val json = """{"books":[{"book":1,"count":2,"from":1,"to":2},{"book":2,"count":1,"from":3,"to":3}],"reportBook":{"1":1,"402b":2}}"""
        // The production endpoint has 97 rows; this focused fixture exercises report parsing separately.
        assertEquals(mapOf("1" to 1, "402b" to 2), BukhariCatalogParser.parseReportBook(json))
        assertTrue(BukhariCatalogParser.isValidHadithId("402b"))
        assertFalse(BukhariCatalogParser.isValidHadithId("402x2"))
        assertEquals("1", BukhariCatalogParser.normalizeHadithId("0001"))
        assertEquals("402b", BukhariCatalogParser.normalizeHadithId("0402B"))
    }

    @Test fun parsesArabicTokensAndMapsToExistingEntryShape() {
        val json = """{"book":1,"hadith":[{"n":"1","isnad":"راوٍ","tokens":[{"id":"x","text":"إِنَّمَا"},{"id":"y","text":"الأَعْمَالُ"}]}]}"""
        val book = BukhariBook(1, 1, 1, 1, "Revelation")
        val entry = BukhariCatalogParser.parseBook(json, book).single()
        assertEquals("1", entry.number)
        assertEquals("راوٍ", entry.isnad)
        assertEquals("إِنَّمَا الأَعْمَالُ", entry.arabic)
        assertEquals("Sahih al-Bukhari", entry.asHadithEntry().collection)
    }

    @Test fun parsesTranslationAndFiltersRequestedHadith() {
        val json = """{"hadiths":[{"hadithnumber":1,"text":"first"},{"hadithnumber":2,"text":"second"}]}"""
        assertEquals("second", BukhariCatalogParser.parseTranslation(json, "eng", "2")?.text)
        assertEquals(null, BukhariCatalogParser.parseTranslation(json, "urd", "9"))
    }

    @Test fun validatesOnlyTrustedHttpsHosts() {
        assertEquals("https://www.hadith.to/bukhari/index.json", validateCatalogUrl("https://www.hadith.to/bukhari/index.json"))
        assertEquals("https://cdn.jsdelivr.net/gh/a", validateCatalogUrl("https://cdn.jsdelivr.net/gh/a"))
        assertFails { validateCatalogUrl("http://www.hadith.to/bukhari/index.json") }
        assertFails { validateCatalogUrl("https://evil.example/bukhari/index.json") }
        assertFails { validateCatalogUrl("https://www.hadith.to/bukhari/index.json?redirect=evil") }
    }

    @Test fun suffixHadithKeepsItsOwnUnavailableMediaPaths() {
        val detail = CatalogHadith(
            number = "402b",
            book = BukhariBook(8, 1, 402, 402, "Prayers (Salat)"),
            isnad = "",
            words = listOf(HadithWord("قَالَ", "", "", null)),
        ).asBukhariDetail()

        assertEquals(
            "https://pub-4c1d62290e264660b4061d58417926be.r2.dev/bukhari-timings/n402b.json",
            detail.audioSource.timingUrl,
        )
        assertEquals(
            "https://pub-4c1d62290e264660b4061d58417926be.r2.dev/bukhari/402b.mp3",
            detail.audioSource.fallbackAudioUrl,
        )
        assertTrue(detail.audioSource.displayTimingTokens)
    }

    @Test fun repositoryNormalizesLeadingZerosAndFetchesTheIndexOnce() = runBlocking {
        val rows = (1..97).joinToString(",") { number ->
            """{"book":$number,"count":1,"from":$number,"to":$number}"""
        }
        val index = """{"books":[$rows],"reportBook":{"1":1}}"""
        val calls = mutableListOf<String>()
        val repository = BukhariCatalogRepository { url ->
            calls += url
            when (url) {
                BukhariCatalogRepository.INDEX_URL -> index
                "${BukhariCatalogRepository.BASE_URL}/book-1.json" ->
                    """{"book":1,"hadith":[{"n":"1","isnad":"","tokens":[{"text":"قَالَ"}]}]}"""
                else -> error("Unexpected URL $url")
            }
        }

        assertEquals("1", repository.hadith("0001").number)
        assertEquals(1, calls.count { it == BukhariCatalogRepository.INDEX_URL })
    }

    private fun assertFails(block: () -> Unit) {
        try { block(); throw AssertionError("Expected validation failure") } catch (_: IllegalArgumentException) { }
    }
}
